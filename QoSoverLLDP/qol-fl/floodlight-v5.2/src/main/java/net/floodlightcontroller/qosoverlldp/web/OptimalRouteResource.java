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

public class OptimalRouteResource extends ServerResource {
    private final String OptimalROUTEFILE = System.getProperty("user.dir")+"/GetOptimalRoute.py";
    private final String GetOptimalRoutepyHead =
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
        "import sys\n" +
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
        "    begin_insert = len(shortest_path)\n" +
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
        "def Get_OptimalRoute(src_host,dst_host,src_sw,dst_sw,Error_list,shortest_path,begin_insert):\n" +
        "    #获取网络所有链路的列表\n" +
        "    url = 'http://localhost:8080/wm/topology/links/json'\n" +
        "    links = json.loads(urllib2.urlopen(url).read())\n";
    private final String GetOptimalRoutepyTail =
        "#    print src_dst_sw_port\n" +
        "    G = nx.Graph()\n" +
        "    for s in switches:\n" +
        "        G.add_node(s)\n" +
        "    G.add_weighted_edges_from(topo)\n" +
        "#    pos = nx.spectral_layout(G)\n" +
        "#    nx.draw(G,pos,with_labels=True,node_size = 1,font_size=24,font_color='red')\n" +
        "#    plt.savefig(\"Graph.png\")\n" +
        "    try:\n" +
        "        #生成最优路径\n" +
        "        shortest_sw_path = nx.dijkstra_path(G,src_sw,dst_sw)\n" +
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
        "        Error_list,shortest_path = Get_OptimalRoute(src_host,dst_host,src_sw,dst_sw,Error_list,shortest_path,begin_insert)\n" +
        "    if(len(Error_list) is 0 and len(shortest_path) is 0):\n" +
        "        Error_list.append('unknown error')\n" +
        "        print json.dumps(Error_list)\n" +
        "    elif(len(Error_list) is not 0 and len(shortest_path) is 0):\n" +
        "        print json.dumps(Error_list)\n" +
        "    elif(len(Error_list) is 0 and len(shortest_path) is not 0):\n" +
        "        print json.dumps(shortest_path)\n";
    private final String GetOptimalRoutepyMiddleofBandwidth =
        "    url2 = 'http://localhost:8080/wm/qosoverlldp/qos/all/periodic/json'\n" +
        "    qosdb = json.loads(urllib2.urlopen(url2).read())\n" +
        "    #获取拓扑的交换机链路信息形成图\n" +
        "    switchportvalue = dict()\n" +
        "    for qos in qosdb:\n" +
        "        switchid = int(qos['switchid'].replace(':',''),16)\n" +
        "        portid = qos['portid']\n" +
        "        bandwidth = long(qos['bandwidth'].replace('bit',''))\n" +
        "        #迪杰斯特拉算法是根据加权的值的大小来得出路径，但是带宽越大越好，所以要逆转一下用100000000来除\n" +
        "        switchportvalue.setdefault('s'+str(switchid)+'-eth'+portid,\n" +
        "                                   (lambda x,y: 1 if x/y <1 else x/y)(100000000,bandwidth))\n" +
        "    topo = set(tuple())\n" +
        "    switches = set()\n" +
        "    src_dst_sw_port = dict(tuple())\n" +
        "    for link in links:\n" +
        "        src = int(link['src-switch'].replace(':',''),16)\n" +
        "        dst = int(link['dst-switch'].replace(':',''),16)\n" +
        "        src_port = int(link['src-port'])\n" +
        "        dst_port = int(link['dst-port'])\n" +
        "        src_dst_sw_port.setdefault(str(src)+str(dst),(src_port,dst_port))\n" +
        "        #取最小值\n" +
        "        weight = ( lambda x, y: x if x > y else y )( \n" +
        "        switchportvalue.get('s'+str(src)+'-eth'+str(src_port)), \n" +
        "        switchportvalue.get('s'+str(dst)+'-eth'+str(dst_port)))\n" +
        "        topo.add((src,dst,weight))\n" +
        "        switches.add(src)\n" +
        "        switches.add(dst)\n";
    private final String GetOptimalRoutepyMiddleofDelay =
        "    url2 = 'http://localhost:8080/wm/qosoverlldp/qos/all/periodic/json'\n" +
        "    qosdb = json.loads(urllib2.urlopen(url2).read())\n" +
        "    #获取拓扑的交换机链路信息形成图\n" +
        "    switchportvalue = dict()\n" +
        "    for qos in qosdb:\n" +
        "        switchid = int(qos['switchid'].replace(':',''),16)\n" +
        "        portid = qos['portid']\n" +
        "        delay = long(qos['delay'].replace('us',''))\n" +
        "        switchportvalue.setdefault('s'+str(switchid)+'-eth'+portid,delay)\n" +
        "    topo = set(tuple())\n" +
        "    switches = set()\n" +
        "    src_dst_sw_port = dict(tuple())\n" +
        "    for link in links:\n" +
        "        src = int(link['src-switch'].replace(':',''),16)\n" +
        "        dst = int(link['dst-switch'].replace(':',''),16)\n" +
        "        src_port = int(link['src-port'])\n" +
        "        dst_port = int(link['dst-port'])\n" +
        "        src_dst_sw_port.setdefault(str(src)+str(dst),(src_port,dst_port))\n" +
        "        #求和\n" +
        "        weight = switchportvalue.get('s'+str(src)+'-eth'+str(src_port))+\\\n" +
        "        switchportvalue.get('s'+str(dst)+'-eth'+str(dst_port))\n" +
        "        topo.add((src,dst,weight))\n" +
        "        switches.add(src)\n" +
        "        switches.add(dst)\n";
    private final String GetOptimalRoutepyMiddleofJitter =
        "    url2 = 'http://localhost:8080/wm/qosoverlldp/qos/all/periodic/json'\n" +
        "    qosdb = json.loads(urllib2.urlopen(url2).read())\n" +
        "    #获取拓扑的交换机链路信息形成图\n" +
        "    switchportvalue = dict()\n" +
        "    for qos in qosdb:\n" +
        "        switchid = int(qos['switchid'].replace(':',''),16)\n" +
        "        portid = qos['portid']\n" +
        "        jitter = long(qos['jitter'].replace('us',''))\n" +
        "        switchportvalue.setdefault('s'+str(switchid)+'-eth'+portid,jitter)\n" +
        "    topo = set(tuple())\n" +
        "    switches = set()\n" +
        "    src_dst_sw_port = dict(tuple())\n" +
        "    for link in links:\n" +
        "        src = int(link['src-switch'].replace(':',''),16)\n" +
        "        dst = int(link['dst-switch'].replace(':',''),16)\n" +
        "        src_port = int(link['src-port'])\n" +
        "        dst_port = int(link['dst-port'])\n" +
        "        src_dst_sw_port.setdefault(str(src)+str(dst),(src_port,dst_port))\n" +
        "        #求和\n" +
        "        weight = switchportvalue.get('s'+str(src)+'-eth'+str(src_port))+\\\n" +
        "        switchportvalue.get('s'+str(dst)+'-eth'+str(dst_port))\n" +
        "        topo.add((src,dst,weight))\n" +
        "        switches.add(src)\n" +
        "        switches.add(dst)\n";
    private final String GetOptimalRoutepyMiddleofLoss =
        "    url2 = 'http://localhost:8080/wm/qosoverlldp/qos/all/periodic/json'\n" +
        "    qosdb = json.loads(urllib2.urlopen(url2).read())\n" +
        "    #获取拓扑的交换机链路信息形成图\n" +
        "    switchportvalue = dict()\n" +
        "    for qos in qosdb:\n" +
        "        switchid = int(qos['switchid'].replace(':',''),16)\n" +
        "        portid = qos['portid']\n" +
        "        loss = float(qos['loss'].replace('%',''))\n" +
        "        switchportvalue.setdefault('s'+str(switchid)+'-eth'+portid,loss)\n" +
        "    topo = set(tuple())\n" +
        "    switches = set()\n" +
        "    src_dst_sw_port = dict(tuple())\n" +
        "    for link in links:\n" +
        "        src = int(link['src-switch'].replace(':',''),16)\n" +
        "        dst = int(link['dst-switch'].replace(':',''),16)\n" +
        "        src_port = int(link['src-port'])\n" +
        "        dst_port = int(link['dst-port'])\n" +
        "        src_dst_sw_port.setdefault(str(src)+str(dst),(src_port,dst_port))\n" +
        "        #相乘\n" +
        "        weight = switchportvalue.get('s'+str(src)+'-eth'+str(src_port))*\\\n" +
        "        switchportvalue.get('s'+str(dst)+'-eth'+str(dst_port))\n" +
        "        topo.add((src,dst,weight))\n" +
        "        switches.add(src)\n" +
        "        switches.add(dst)\n";
    private final String GetOptimalRoutepyMiddleofLatency =
        "    url2 = 'http://localhost:8080/wm/qosoverlldp/qos/all/periodic/json'\n" +
        "    qosdb = json.loads(urllib2.urlopen(url2).read())\n" +
        "    #获取拓扑的交换机链路信息形成图\n" +
        "    switchportvalue = dict()\n" +
        "    for qos in qosdb:\n" +
        "        switchid = int(qos['switchid'].replace(':',''),16)\n" +
        "        portid = qos['portid']\n" +
        "        jitter = long(qos['latency'].replace('us',''))\n" +
        "        switchportvalue.setdefault('s'+str(switchid)+'-eth'+portid,jitter)\n" +
        "    topo = set(tuple())\n" +
        "    switches = set()\n" +
        "    src_dst_sw_port = dict(tuple())\n" +
        "    for link in links:\n" +
        "        src = int(link['src-switch'].replace(':',''),16)\n" +
        "        dst = int(link['dst-switch'].replace(':',''),16)\n" +
        "        src_port = int(link['src-port'])\n" +
        "        dst_port = int(link['dst-port'])\n" +
        "        src_dst_sw_port.setdefault(str(src)+str(dst),(src_port,dst_port))\n" +
        "        #求和\n" +
        "        weight = switchportvalue.get('s'+str(src)+'-eth'+str(src_port))+\\\n" +
        "        switchportvalue.get('s'+str(dst)+'-eth'+str(dst_port))\n" +
        "        topo.add((src,dst,weight))\n" +
        "        switches.add(src)\n" +
        "        switches.add(dst)\n";
    private final String GetOptimalRoutepyMiddleofTotal =
        "    url2 = 'http://localhost:8080/wm/qosoverlldp/qos/all/periodic/json'\n" +
        "    qosdb = json.loads(urllib2.urlopen(url2).read())\n" +
        "    #获取拓扑的交换机链路信息形成图\n" +
        "    switchportvalue = dict()\n" +
        "    for qos in qosdb:\n" +
        "        switchid = int(qos['switchid'].replace(':',''),16)\n" +
        "        portid = qos['portid']\n" +
        "        bandwidth = float(qos['bandwidth'].replace('bit',''))\n" +
        "        delay = float(qos['delay'].replace('us',''))\n" +
        "        jitter = float(qos['jitter'].replace('us',''))\n" +
        "        loss = float(qos['loss'].replace('%',''))\n" +
        "        latency = float(qos['latency'].replace('us',''))\n" +
        "        total = abs(bandwidth/250+20-delay/5+10-jitter/2.5+10-loss/10+(latency-90)*2)\n" +
        "        switchportvalue.setdefault('s'+str(switchid)+'-eth'+portid,total)\n" +
        "    topo = set(tuple())\n" +
        "    switches = set()\n" +
        "    src_dst_sw_port = dict(tuple())\n" +
        "    for link in links:\n" +
        "        src = int(link['src-switch'].replace(':',''),16)\n" +
        "        dst = int(link['dst-switch'].replace(':',''),16)\n" +
        "        src_port = int(link['src-port'])\n" +
        "        dst_port = int(link['dst-port'])\n" +
        "        src_dst_sw_port.setdefault(str(src)+str(dst),(src_port,dst_port))\n" +
        "        #求和\n" +
        "        weight = switchportvalue.get('s'+str(src)+'-eth'+str(src_port))+\\\n" +
        "        switchportvalue.get('s'+str(dst)+'-eth'+str(dst_port))\n" +
        "        topo.add((src,dst,weight))\n" +
        "        switches.add(src)\n" +
        "        switches.add(dst)\n";

    private final String OPTIMAL_BANDWITH = "bandwidth";
    private final String OPTIMAL_DELAY = "delay";
    private final String OPTIMAL_JITTER = "jitter";
    private final String OPTIMAL_LOSS = "loss";
    private final String OPTIMAL_LATENCY = "latency";
    private final String OPTIMAL_TOTAL = "total";

    @Get("json")
    public String retrieve(){
        String optimaltype = (String)getRequestAttributes().get("optimal-type");//优化的类型
        String GetOptimalRoutePy = new String();
        try{
            File file =new File(OptimalROUTEFILE);
            if(!file.exists()){
                file.createNewFile();
            }
            FileWriter fileWritter = new FileWriter(file.getName(),true);
            fileWritter.flush();
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            if(optimaltype.equals(OPTIMAL_BANDWITH)){
                GetOptimalRoutePy = GetOptimalRoutepyHead + GetOptimalRoutepyMiddleofBandwidth + GetOptimalRoutepyTail;
            }else if(optimaltype.equals(OPTIMAL_DELAY)){
                GetOptimalRoutePy = GetOptimalRoutepyHead + GetOptimalRoutepyMiddleofDelay + GetOptimalRoutepyTail;
            }else if(optimaltype.equals(OPTIMAL_JITTER)){
                GetOptimalRoutePy = GetOptimalRoutepyHead + GetOptimalRoutepyMiddleofJitter + GetOptimalRoutepyTail;
            }else if(optimaltype.equals(OPTIMAL_LOSS)){
                GetOptimalRoutePy = GetOptimalRoutepyHead + GetOptimalRoutepyMiddleofLoss + GetOptimalRoutepyTail;
            }else if(optimaltype.equals(OPTIMAL_LATENCY)){
                GetOptimalRoutePy = GetOptimalRoutepyHead + GetOptimalRoutepyMiddleofLatency + GetOptimalRoutepyTail;
            }else if(optimaltype.equals(OPTIMAL_TOTAL)){
                GetOptimalRoutePy = GetOptimalRoutepyHead + GetOptimalRoutepyMiddleofTotal + GetOptimalRoutepyTail;
            }
            bufferWritter.write(GetOptimalRoutePy);
            bufferWritter.close();
        }catch(Exception e){
            return e.getMessage();
        }
        if(!GetOptimalRoutePy.isEmpty()){
            String src_host = (String)getRequestAttributes().get("src-host");
            String dst_host = (String)getRequestAttributes().get("dst-host");
            if(!(Pattern.compile("^[s0-9]+$").matcher(src_host).matches()||Pattern.compile("^[h0-9]+$").matcher(src_host).matches())&&
                    !(Pattern.compile("^[s0-9]+$").matcher(dst_host).matches()||Pattern.compile("^[h0-9]+$").matcher(dst_host).matches())){
                return "input error! please s+integer or h+integer";
            }
            InputStream in;
            try {
                Process pro = Runtime.getRuntime().exec(new String[]{"python",
                        OptimalROUTEFILE,src_host,dst_host});
                pro.waitFor();
                in = pro.getInputStream();
                BufferedReader read = new BufferedReader(new InputStreamReader(in));
                File file =new File(OptimalROUTEFILE);
                file.delete();
                return read.readLine();
            } catch (Exception e) {
                File file =new File(OptimalROUTEFILE);
                file.delete();
                e.printStackTrace();
            }
            return "error: Accidental error, can't get optimal path please try cmd: pingall";
        }else{
            return "error: optimal type ,please input bandwidth or delay or jitter or loss or latency or total";
        }
    }
}
