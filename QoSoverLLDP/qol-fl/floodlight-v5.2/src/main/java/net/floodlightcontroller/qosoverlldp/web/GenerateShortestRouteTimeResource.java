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
import java.util.regex.Pattern;

public class GenerateShortestRouteTimeResource extends ServerResource {

    private final String GenerateShortestROUTETimeFILE = System.getProperty("user.dir")+"/GenerateShortestRouteTime.py";
    private final String GenerateShortestROUTETimepy =
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
        "import sys,datetime\n" +
        "from itertools import islice\n" +
        "\n" +
        "def Get_SrcSw_DstSw(src_host,dst_host,Error_list,shortest_path,begin_insert):\n" +
        "    #获取网络所有设备的列表\n" +
        "    url = 'http://localhost:8080/wm/qosoverlldp/device/all/json'\n" +
        "    device = json.loads(urllib2.urlopen(url).read())['devices']\n" +
        "    #获取主机与交换机的关系\n" +
        "    host_sw = dict()\n" +
        "    sw_port = dict()\n" +
        "    for dev in device:\n" +
        "        host = None\n" +
        "        sw = None\n" +
        "        for h in dev['mac']:\n" +
        "            if h is not None:\n" +
        "                host = int(h.replace(':',''),16)\n" +
        "        for s in dev['attachmentPoint']:\n" +
        "            if s is not None:\n" +
        "                sw = int(s['switch'].replace(':',''),16)\n" +
        "                port = int(s['port'])\n" +
        "                sw_port.setdefault(host,port)\n" +
        "        host_sw.setdefault(host,sw)\n" +
        "    src_sw, dst_sw = None, None\n" +
        "    if 's' in src_host:#如果源主机是交换机\n" +
        "        src_sw = int(src_host.replace('s',''))\n" +
        "    elif 'h' in src_host:\n" +
        "        src_host = int(src_host.replace('h',''))\n" +
        "        try:\n" +
        "            src_sw = host_sw[src_host]\n" +
        "            shortest_path.append('h'+str(src_host))\n" +
        "            shortest_path.append('s'+str(src_sw)+'-eth'+str(sw_port[src_host]))\n" +
        "        except:\n" +
        "            shortest_path = []\n" +
        "            Error_list.append('h'+str(src_host)+' is nonexist')\n" +
        "    begin_insert=len(shortest_path)\n" +
        "    if 's' in dst_host:\n" +
        "        dst_sw = int(dst_host.replace('s',''))\n" +
        "    elif 'h' in dst_host:\n" +
        "        dst_host = int(dst_host.replace('h',''))\n" +
        "        try:\n" +
        "            dst_sw = host_sw[dst_host]\n" +
        "            shortest_path.append('s'+str(dst_sw)+'-eth'+str(sw_port[dst_host]))\n" +
        "            shortest_path.append('h'+str(dst_host))\n" +
        "        except:\n" +
        "            shortest_path = []\n" +
        "            Error_list.append('h'+str(dst_host)+' is nonexist')\n" +
        "    return src_sw,dst_sw,Error_list,shortest_path,begin_insert\n" +
        "\n" +
        "def Get_ShortestRoute(src_host,dst_host,src_sw,dst_sw,Error_list,shortest_path,begin_insert):\n" +
        "    #获取网络所有链路的列表\n" +
        "    url = 'http://localhost:8080/wm/topology/links/json'\n" +
        "    links = json.loads(urllib2.urlopen(url).read())\n" +
        "    #获取拓扑的交换机链路信息形成图\n" +
        "    topo = set(tuple())\n" +
        "    sw = set()\n" +
        "    src_dst_sw_port = dict(tuple())\n" +
        "    for link in links:\n" +
        "        src = int(link['src-switch'].replace(':',''),16)\n" +
        "        dst = int(link['dst-switch'].replace(':',''),16)\n" +
        "        src_port = int(link['src-port'])\n" +
        "        dst_port = int(link['dst-port'])\n" +
        "        src_dst_sw_port.setdefault(str(src)+str(dst),(src_port,dst_port))\n" +
        "        topo.add((src,dst))\n" +
        "        sw.add(src)\n" +
        "        sw.add(dst)\n" +
        "#    print src_dst_sw_port\n" +
        "    G = nx.Graph()\n" +
        "    for s in sw:\n" +
        "        G.add_node(s)\n" +
        "    G.add_edges_from(topo)\n" +
        "#    pos = nx.spectral_layout(G)\n" +
        "#    nx.draw(G,pos,with_labels=True,node_size = 1,font_size=24,font_color='red')\n" +
        "#    plt.savefig(\"Graph.png\")\n" +
        "    try:\n" +
        "        #生成最短路径\n" +
        "        t1=datetime.datetime.now().microsecond\n" +
        "        shortest_sw_path = nx.dijkstra_path(G,src_sw,dst_sw)\n" +
        "        t2=datetime.datetime.now().microsecond\n" +
        "        dijkstra_path_time=str(t2-t1)+\"ms\"\n" +
        "        t3=datetime.datetime.now().microsecond\n" +
        "        nx.all_pairs_shortest_path(G)[src_sw][dst_sw]\n" +
        "        t4=datetime.datetime.now().microsecond\n" +
        "        all_shortest_path_time=str(t4-t3)+\"ms\"\n" +
        "        def k_shortest_paths(G, source, target, k, weight=None):\n" +
        "            return list(islice(nx.shortest_simple_paths(G, source, target),k))\n" +
        "        t5=datetime.datetime.now().microsecond\n" +
        "        k_shortest_paths(G,src_sw,dst_sw,1)\n" +
        "        t6=datetime.datetime.now().microsecond\n" +
        "        k_shortest_paths_time=str(t6-t5)+\"ms\"\n" +
        "        if(len(shortest_sw_path)>=2):\n" +
        "    #        print shortest_sw_path\n" +
        "            for i in range(0,len(shortest_sw_path)-1):\n" +
        "                src_sw = str(shortest_sw_path[i])\n" +
        "                dst_sw = str(shortest_sw_path[i+1])\n" +
        "                src_dst_port = None\n" +
        "                src_dst_port = src_dst_sw_port.get(src_sw+dst_sw)\n" +
        "                if(src_dst_port is None):\n" +
        "                    src_dst_port = src_dst_sw_port.get(dst_sw+src_sw)\n" +
        "                    shortest_path.insert(begin_insert,'s'+src_sw+'-eth'+str(src_dst_port[1]))\n" +
        "                    begin_insert += 1\n" +
        "                    shortest_path.insert(begin_insert,'s'+dst_sw+'-eth'+str(src_dst_port[0]))\n" +
        "                    begin_insert += 1\n" +
        "                else:\n" +
        "                    shortest_path.insert(begin_insert,'s'+src_sw+'-eth'+str(src_dst_port[0]))\n" +
        "                    begin_insert += 1\n" +
        "                    shortest_path.insert(begin_insert,'s'+dst_sw+'-eth'+str(src_dst_port[1]))\n" +
        "                    begin_insert += 1\n" +
        "        shortest_path.append({\"dijkstra_path_time\":dijkstra_path_time})\n" +
        "        shortest_path.append({\"all_shortest_path_time\":all_shortest_path_time})\n" +
        "        shortest_path.append({\"k_shortest_paths_time\":k_shortest_paths_time})\n" +
        "    except Exception,e:\n" +
        "        print e\n" +
        "        Error_list = []\n" +
        "        shortest_path = []\n" +
        "        if 's' not in src_host:\n" +
        "            src_host = 'h' + src_host\n" +
        "        if 's' not in dst_host:\n" +
        "            dst_host = 'h' + dst_host\n" +
        "        Error_list.append(str(src_host)+' to '+str(dst_host)+' unreachable')\n" +
        "    return Error_list,shortest_path\n" +
        "\n" +
        "if __name__ == '__main__':\n" +
        "    src_host = str(sys.argv[1])\n" +
        "    dst_host = str(sys.argv[2])\n" +
        "    Error_list = list()\n" +
        "    shortest_path = list()\n" +
        "    begin_insert = 0\n" +
        "    src_sw,dst_sw,Error_list,shortest_path,begin_insert = Get_SrcSw_DstSw(src_host,dst_host,Error_list,shortest_path,begin_insert)\n" +
        "    if(len(Error_list) is 0):\n" +
        "        Error_list,shortest_path = Get_ShortestRoute(src_host,dst_host,src_sw,dst_sw,Error_list,shortest_path,begin_insert)\n" +
        "    if(len(Error_list) is 0 and len(shortest_path) is 0):\n" +
        "        Error_list.append('unknown error')\n" +
        "        print json.dumps(Error_list)\n" +
        "    elif(len(Error_list) is not 0 and len(shortest_path) is 0):\n" +
        "        print json.dumps(Error_list)\n" +
        "    elif(len(Error_list) is 0 and len(shortest_path) is not 0):\n" +
        "        print json.dumps(shortest_path)\n";

    @Get("json")
    public Object retrieve(){
        try{
            File file =new File(GenerateShortestROUTETimeFILE);
            if(!file.exists()){
                file.createNewFile();
            }
            FileWriter fileWritter = new FileWriter(file.getName(),true);
            fileWritter.flush();
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            bufferWritter.write(GenerateShortestROUTETimepy);
            bufferWritter.close();
        }catch(Exception e){
            return e.getMessage();
        }
        String src_host = (String)getRequestAttributes().get("src-host");
        String dst_host = (String)getRequestAttributes().get("dst-host");
        if(!(Pattern.compile("^[s0-9]+$").matcher(src_host).matches()||Pattern.compile("^[h0-9]+$").matcher(src_host).matches())&&
                !(Pattern.compile("^[s0-9]+$").matcher(dst_host).matches()||Pattern.compile("^[h0-9]+$").matcher(dst_host).matches())){
            return "input error! please s+integer or h+integer";
        }
        InputStream in;
        try {
            Process pro = Runtime.getRuntime().exec(new String[]{"python",
                    GenerateShortestROUTETimeFILE,src_host,dst_host});
            pro.waitFor();
            in = pro.getInputStream();
            BufferedReader read = new BufferedReader(new InputStreamReader(in));
            File file =new File(GenerateShortestROUTETimeFILE);
            file.delete();
            return read.readLine();
        } catch (Exception e) {
            File file =new File(GenerateShortestROUTETimeFILE);
            file.delete();
            e.printStackTrace();
        }
        return "error: Accidental error, can't get shortest path please try cmd: pingall";
    }

}
