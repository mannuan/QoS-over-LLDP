/**
 * Copyright (c) 2016 - 2017 SWUNIX Lab, swunix.com, Inc.
 *
 *  You may obtain a copy of the License at:
 *
 *       http://www.swunix.com
 *
 *       vlog默认的输出文件是/var/log/openvswitch/ovs-vswitchd.log
 *       在floodlight上面设置qostlv的时候要注意了,不要把qostlv的位置放在timestamp的后面，否则就会影响floodlight的功能
 *       tc查看命令：带宽 tc class show dev s1-eth1, 延迟、抖动、丢包率 tc qdisc show dev s1-eth1
 *       使用tc命令把qos设置为0，是无法设置的，所以在判断0的时候无需考虑太多
 *       带宽和丢包率这两个qos值是独立的，不受其他qos信息的影响,但是延迟和抖动是相互影响的，唯有设置了延迟才可以设置抖动，
 *       因此，在openvswitch设置jitter的时候应该先判断有无delay,否则不需要判断
 *       注意：linux 的tc 支持带宽设置的范围为:8bit~1Gbit;(而且只能8bit的设置)单位有:bit,Kbit,Mbit,Gbit
 *                   delay延迟的范围(0,274877906]us,mininet设置的单位默认是us,也就是延迟最大是247.9s，然后tc设置时间的时候只保留到小数点后一位
 *                   单位有:us,ms,s
 *                   抖动(jitter)的范围(0,274877906]        单位:us 注意:jitter是依赖于delay的,也就是说没有delay就没有jitter,有delay但是可以没有jitter
 *                   丢包率(delay)的范围[0,100]
 *       在floodlight中加入qostlv时千万不可以改变chassisid和portidtlv的位置,其他随便,否则qos over lldp项目无法实现,但是不会影响交换机的正常工作
 *       在mininet中设置0.000008Mbit(8bit)的时候，在终端可能显示0.00Mbit,但是确实设置进去了,如下:
 *       class htb 5:1 root leaf 10: prio 0 rate 8bit ceil 8bit burst 1b cburst 225b
 *       把实现qostlv的功能加在了net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager.generateLLDPMessage方法里面
 *       只需要一行代码:lldp.getOptionalTLVList().add(qosTLV);(放这行代码要注意:不要把qostlv的位置放在timestamp的后面)
 *       另外由于要改lldp发送的时间间隔,所以要改net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager中的LLDP_TO_ALL_INTERVAL参数,
 *       把protected final int LLDP_TO_ALL_INTERVAL = 15;把protected final修饰符改为public static修饰符
 *       还有要/wm/qosoverlldp/proactive/json立刻生效就要修改lldpClock的属性把protected final 改为public static
 *       把protected final long lldpClock = 0;把protected final修饰符改为public static修饰符
 *       把对LLDP进行QoS信息的填写的功能加在packet_out解码的函数里面，具体位置是ofp-util.c的文件的ofputil_decode_packet_out函数里面，大概在文件的第4190行
 *		 填写的位置如下所示：
 *
 *		 struct ofpbuf b = ofpbuf_const_initializer(oh, ntohs(oh->length));
 *
 *		 add_qos_to_lldp(&b);
 *
 *		 也就是放在ofpbuf下面，因为我们的主程序要调用它
 *
 *		 在进行floodlight编程的时候，一定要注意：千万不要乱导包，导入的外部包类名如果与原来的包冲突，就会影响整个floodlight的正常运行
 *		 为了使QoSoverLLDP模块生效，要在如下两个配置文件里面添加net.floodlightcontroller.qosoverlldp.internal.QoSoverLLDPManager
 *		 qol-fl/floodlight-v5.0/src/main/resources/META-INF/services/net.floodlightcontroller.core.module.IFloodlightModule
 *		 qol-fl/floodlight-v5.0/src/main/resources/floodlightdefault.properties
 *		 以上两个文件的添加方式是不同的，第一个配置只需要加在文件末尾即可
 *		 但是第二个配置文件，必须考虑清楚，应该如下：
 *		 net.floodlightcontroller.statistics.StatisticsCollector,\
 *       net.floodlightcontroller.qosoverlldp.internal.QoSoverLLDPManager
 *       也就是说上面要加的那句话应该放在net.floodlightcontroller这类模块的最后，并与上一个模块用逗号隔开
 */

package net.floodlightcontroller.qosoverlldp.web;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import java.io.*;

public class AllShortestRoutesResource extends ServerResource{

    private final String AllShortestROUTEFILE = System.getProperty("user.dir")+"/GetAllShortestRoute.py";
    private final String GetAllShortestRoutepy =
        "# -*- coding: utf-8 -*-\n" +
        "\"\"\"\n" +
        "Created on Sat Dec 31 18:32:27 2016\n" +
        "\n" +
        "@author: wjl\n" +
        "\"\"\"\n" +
        "import networkx as nx\n" +
        "import matplotlib.pyplot as plt\n" +
        "import urllib2\n" +
        "import json\n" +
        "\n" +
        "#获取网络所有设备的列表\n" +
        "url = 'http://localhost:8080/wm/qosoverlldp/device/all/json'\n" +
        "devices = json.loads(urllib2.urlopen(url).read())['devices']\n" +
        "for i in range(0,len(devices)):\n" +
        "    mac=devices[i]['mac']\n" +
        "    host=i+1\n" +
        "    for j in range(len(mac)):#一个主机不可以出现两个mac地址\n" +
        "        host=int(str(mac[j]).replace(':',''),16)\n" +
        "    attachmentPoint=devices[i]['attachmentPoint']\n" +
        "    dev=\"\"\n" +
        "    for j in range(len(attachmentPoint)):\n" +
        "        switch=int(str(attachmentPoint[j]['switch']).replace(':',''),16)\n" +
        "        port=int(attachmentPoint[j]['port'])\n" +
        "        dev='s%d-eth%d'%(switch,port)\n" +
        "    devices[i]=['h%d'%host,dev,switch]\n" +
        "#获取网络所有链路的列表\n" +
        "url = 'http://localhost:8080/wm/topology/links/json'\n" +
        "links = json.loads(urllib2.urlopen(url).read())\n" +
        "linksdict=dict()\n" +
        "switches=set()#点的集合\n" +
        "switchconn=list(tuple())#线的列表\n" +
        "for i in range(len(links)):\n" +
        "    src_switch=int(str(links[i]['src-switch']).replace(':',''),16)\n" +
        "    src='s%d-eth%d'%(src_switch,links[i]['src-port'])\n" +
        "    dst_switch=int(str(links[i]['dst-switch']).replace(':',''),16)\n" +
        "    dst='s%d-eth%d'%(dst_switch,links[i]['dst-port'])\n" +
        "    linksdict.setdefault('%d-%d'%(src_switch,dst_switch),[src,dst])\n" +
        "    if((lambda d:True if d in 'bidirectional' else False)(str(links[i]['direction']))):\n" +
        "        linksdict.setdefault('%d-%d'%(dst_switch,src_switch),[dst,src])\n" +
        "    switches.add(src_switch)\n" +
        "    switches.add(dst_switch)\n" +
        "    switchconn.append((src_switch,dst_switch))\n" +
        "#获取拓扑的交换机链路信息形成图\n" +
        "G = nx.Graph()\n" +
        "for s in switches:\n" +
        "    G.add_node(s)\n" +
        "G.add_edges_from(switchconn)\n" +
        "#pos = nx.spectral_layout(G)\n" +
        "#nx.draw(G,pos,with_labels=True,node_size = 1,font_size=24,font_color='red')\n" +
        "#plt.savefig(\"Graph.png\")\n" +
        "allswpath=nx.all_pairs_shortest_path(G)\n" +
        "for i in range(len(allswpath.values())):\n" +
        "    for j in range(len(allswpath.values()[i])):\n" +
        "        conn=allswpath.values()[i].values()[j]\n" +
        "        conn=(lambda x:[] if len(x) < 2 else x)(conn)\n" +
        "        if len(conn) is 2:\n" +
        "            conn=linksdict['%d-%d'%(conn[0],conn[1])]\n" +
        "        elif(len(conn)>2):\n" +
        "            conn2=list()\n" +
        "            for k in range(len(conn)-1):\n" +
        "                conn2.extend(linksdict['%d-%d'%(conn[k],conn[k+1])])\n" +
        "            conn=conn2\n" +
        "        del allswpath.values()[i].values()[j][:]\n" +
        "        allswpath.values()[i].values()[j].extend(conn)\n" +
        "allpath=list()\n" +
        "for src in devices:\n" +
        "    for dst in devices:\n" +
        "        path=[src[0],src[1]]\n" +
        "        path.extend(allswpath[src[2]][dst[2]])\n" +
        "        path.extend([dst[1],dst[0]])\n" +
        "        allpath.append(path)\n" +
        "for src_sw in switches:\n" +
        "    for dst_sw in switches:\n" +
        "        if len(allswpath[src_sw][dst_sw]) is not 0:\n" +
        "            allpath.append(allswpath[src_sw][dst_sw])\n" +
        "        else:\n" +
        "            allpath.append(['s%d'%src_sw])\n" +
        "print json.dumps(allpath)\n";

    @Get("json")
    public String retrieve(){
        try{
            File file =new File(AllShortestROUTEFILE);
            if(!file.exists()){
                file.createNewFile();
            }
            FileWriter fileWritter = new FileWriter(file.getName(),true);
            fileWritter.flush();
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            bufferWritter.write(GetAllShortestRoutepy);
            bufferWritter.close();
        }catch(Exception e){
            return e.getMessage();
        }
        InputStream in;
        try {
            Process pro = Runtime.getRuntime().exec(new String[]{"python", AllShortestROUTEFILE});
            pro.waitFor();
            in = pro.getInputStream();
            BufferedReader read = new BufferedReader(new InputStreamReader(in));
            File file =new File(AllShortestROUTEFILE);
            file.delete();
            return read.readLine();
        } catch (Exception e) {
            File file =new File(AllShortestROUTEFILE);
            file.delete();
            e.printStackTrace();
        }
        return "error: Accidental error, can't get shortest path please try cmd: pingall";
    }

}
