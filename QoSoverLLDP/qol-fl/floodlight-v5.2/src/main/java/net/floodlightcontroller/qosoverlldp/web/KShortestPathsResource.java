package net.floodlightcontroller.qosoverlldp.web;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import java.io.*;
import java.util.regex.Pattern;

public class KShortestPathsResource extends ServerResource {

    private final String KShortestPathsFILE = System.getProperty("user.dir")+"/GetKShortestPaths.py";
    private final String GetKShortestPathspy =
            "# -*- coding: utf-8 -*-\n" +
            "\"\"\"\n" +
            "Created on Mon May 22 10:15:29 2017\n" +
            "\n" +
            "@author: mininet\n" +
            "\"\"\"\n" +
            "import networkx as nx\n" +
            "import matplotlib.pyplot as plt\n" +
            "import urllib2\n" +
            "import json\n" +
            "import sys\n" +
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
            "def Get_ShortestRoute(src_host,dst_host,src_sw,dst_sw,Error_list,shortest_path,begin_insert,k):\n" +
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
            "    def k_shortest_paths(G, source, target, k, weight=None):\n" +
            "        return list(islice(nx.shortest_simple_paths(G, source, target),k))\n" +
            "    try:\n" +
            "        #生成前k条最短路径\n" +
            "        shortest_paths = list()\n" +
            "        shortest_sw_paths = k_shortest_paths(G, src_sw, dst_sw,k)\n" +
            "        for shortest_sw_path in shortest_sw_paths:\n" +
            "            shortest_path_tmp = list()\n" +
            "            shortest_path_tmp.extend(shortest_path)\n" +
            "            begin_insert_tmp = begin_insert\n" +
            "            if(len(shortest_sw_path)>=2):\n" +
            "                for i in range(0,len(shortest_sw_path)-1):\n" +
            "                    src_sw = str(shortest_sw_path[i])\n" +
            "                    dst_sw = str(shortest_sw_path[i+1])\n" +
            "                    src_dst_port = None\n" +
            "                    src_dst_port = src_dst_sw_port.get(src_sw+dst_sw)\n" +
            "                    if(src_dst_port is None):\n" +
            "                        src_dst_port = src_dst_sw_port.get(dst_sw+src_sw)\n" +
            "                        shortest_path_tmp.insert(begin_insert_tmp,'s'+src_sw+'-eth'+str(src_dst_port[1]))\n" +
            "                        begin_insert_tmp += 1\n" +
            "                        shortest_path_tmp.insert(begin_insert_tmp,'s'+dst_sw+'-eth'+str(src_dst_port[0]))\n" +
            "                        begin_insert_tmp += 1\n" +
            "                    else:\n" +
            "                        shortest_path_tmp.insert(begin_insert_tmp,'s'+src_sw+'-eth'+str(src_dst_port[0]))\n" +
            "                        begin_insert_tmp += 1\n" +
            "                        shortest_path_tmp.insert(begin_insert_tmp,'s'+dst_sw+'-eth'+str(src_dst_port[1]))\n" +
            "                        begin_insert_tmp += 1\n" +
            "            shortest_paths.append(shortest_path_tmp)\n" +
            "    except Exception,e:\n" +
            "        print e\n" +
            "        Error_list = []\n" +
            "        shortest_paths = []\n" +
            "        if 's' not in src_host:\n" +
            "            src_host = 'h' + src_host\n" +
            "        if 's' not in dst_host:\n" +
            "            dst_host = 'h' + dst_host\n" +
            "        Error_list.append(str(src_host)+' to '+str(dst_host)+' unreachable')\n" +
            "    return Error_list,shortest_paths\n" +
            "\n" +
            "if __name__ == '__main__':\n" +
            "    src_host = str(sys.argv[1])\n" +
            "    dst_host = str(sys.argv[2])\n" +
            "    k = int(sys.argv[3])\n" +
            "    Error_list = list()\n" +
            "    shortest_path = list()\n" +
            "    begin_insert = 0\n" +
            "    src_sw,dst_sw,Error_list,shortest_path,begin_insert = Get_SrcSw_DstSw(src_host,dst_host,Error_list,shortest_path,begin_insert)\n" +
            "    if(len(Error_list) is 0):\n" +
            "        Error_list,shortest_path = Get_ShortestRoute(src_host,dst_host,src_sw,dst_sw,Error_list,shortest_path,begin_insert,k)\n" +
            "    if(len(Error_list) is 0 and len(shortest_path) is 0):\n" +
            "        Error_list.append('unknown error')\n" +
            "        print json.dumps(Error_list)\n" +
            "    elif(len(Error_list) is not 0 and len(shortest_path) is 0):\n" +
            "        print json.dumps(Error_list)\n" +
            "    elif(len(Error_list) is 0 and len(shortest_path) is not 0):\n" +
            "        print json.dumps(shortest_path)";

    @Get("json")
    public String retrieve() {
        try {
            File file = new File(KShortestPathsFILE);
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fileWritter = new FileWriter(file.getName(), true);
            fileWritter.flush();
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            bufferWritter.write(GetKShortestPathspy);
            bufferWritter.close();
        } catch (Exception e) {
            return e.getMessage();
        }
        String src_host = (String) getRequestAttributes().get("src-host");
        String dst_host = (String) getRequestAttributes().get("dst-host");
        String k = (String) getRequestAttributes().get("k");
        if (!(Pattern.compile("^[s0-9]+$").matcher(src_host).matches() || Pattern.compile("^[h0-9]+$").matcher(src_host).matches()) &&
                !(Pattern.compile("^[s0-9]+$").matcher(dst_host).matches() || Pattern.compile("^[h0-9]+$").matcher(dst_host).matches())) {
            return "input error! please s+integer or h+integer";
        }
        InputStream in;
        try {
            Process pro = Runtime.getRuntime().exec(new String[]{"python",
                    KShortestPathsFILE, src_host, dst_host, k});
            pro.waitFor();
            in = pro.getInputStream();
            BufferedReader read = new BufferedReader(new InputStreamReader(in));
            File file = new File(KShortestPathsFILE);
            file.delete();
            return read.readLine();
        } catch (Exception e) {
            File file = new File(KShortestPathsFILE);
            file.delete();
            e.printStackTrace();
        }
        return "error: Accidental error, can't get k shortest paths please try cmd: pingall";
    }

}