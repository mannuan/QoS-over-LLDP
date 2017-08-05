# -*- coding: utf-8 -*-
"""
Created on Sun May  1 20:16:42 2016
@author: wjl
这个文件里面总共有：
    2个类
    20个方法
其中适用范围广的有:2个类 16个方法
StaticFlowPusher 是一个静态静态流表推送器的类
ACL 描述acl控制器的类
dumpNodeConnections 输出节点的链路信息
dumpNodenexthop() 返回节点的下一跳的接口
dumpNetConnectionsWithoutController() 输出除了控制器以外所有节点链路信息的方法
addWord() 创建关键字的内容为列表的字典 
addWordstr() 创建关键字的内容为字符串的字典
DisplayInfoOfNet()显示网络的基本信息
getnodeNAMEIP() 获取网络节点的ip或姓名
changeSTRintoLIST() 取一个lists和字符串的交集或者取一个字符串里面的节点名与lists不重合的部分
ChangeNetConnintoLinkList() 把网络的链接信息转化成列表
DumpFlowstoFiles() 把每个交换机的流表都输出到文件里面 
CheckSwitchFlowBy98bytes() 这是一个找到交换机流表里面出现98个字节报文的转发，并出现两次的交换机的方法 
in_portOutputIp() 生成一个交换机入口和出口的字典 
srctodstlink() 程序的作用就是输出源主机到目的主机的接口的路径（intf）
StartFloodLight() 启动floodlight
StopFloodLight() 关闭floodlight
DelSwitchAllFlows() 删除网络中所有交换机的所有流表
存在缺陷或适用返回不广的方法：4个
judgeIP()规范IP的格式
StaticFlows() 操作静态流表的方法
ProcessControl()是一个过程控制程序
PathSrctoDst() 这个方法就是把从源主机到目的主机的完整路线打印出来（以->分隔）
"""
from mininet.util import dumpNetConnections
import time
import httplib
import json
import re
import os
#静态流表推送器 
class StaticFlowPusher(object):
    '''
    使用这个类的前提：
        运行的floodlight控制器的版本必须是1.0以上
    这是一个往交换机推送静态流表的类
    提供了删除，添加，获取等
    Rest API的操作
    '''
 
    def __init__(self, server):
        self.server = server
 
    def get(self, data):
        ret = self.rest_call({}, 'GET')
        return json.loads(ret[2])
 
    def set(self, data):
        ret = self.rest_call(data, 'POST')
        return ret[0] == 200
 
    def remove(self, data):
        ret = self.rest_call(data, 'DELETE')
        return ret[0] == 200
 
    def rest_call(self, data, action):
        path = '/wm/staticflowpusher/json'
        headers = {
            'Content-type': 'application/json',
            'Accept': 'application/json',
            }
        body = json.dumps(data)
        conn = httplib.HTTPConnection(self.server, 8080)
        conn.request(action, path, body, headers)
        response = conn.getresponse()
        ret = (response.status, response.reason, response.read())
        print ret
        conn.close()
        return ret
#acl控制器
class ACL(object):
    '''
    使用前提：
    floodlight的版本必须是1.2及以上
    这是一个往控制器添加访问控制列表(acl)的类
    它提供了添加acl,删除acl的方法
    '''
    def __init__(self, server):
        self.server = server
 
    def get(self, data):#不可用floodlight没有提供接口
        #使用curl http://controller-ip:8080/wm/acl/rules/json代替
        ret = self.rest_call({}, 'GET')
        return json.loads(ret[2])
 
    def set(self, data):
        ret = self.rest_call(data, 'POST')
        return ret[0] == 200
 
    def remove(self, data):
        ret = self.rest_call(data, 'DELETE')
        return ret[0] == 200
 
    def rest_call(self, data, action):
        path = '/wm/acl/rules/json'
        headers = {
            'Content-type': 'application/json',
            'Accept': 'application/json',
            }
        body = json.dumps(data)
        conn = httplib.HTTPConnection(self.server, 8080)
        conn.request(action, path, body, headers)
        response = conn.getresponse()
        ret = (response.status, response.reason, response.read())
        print ret
        conn.close()
        return ret
#输出节点的链路信息
def dumpNodeConnections( nodes ,segment1='',segment2='##'):
    "Dump connections to/from nodes."
    '''
    参数解释：
    segment1 表示一个link两个接口直接的分隔符，默认值为：''(表示不分隔)
    segment2 表示两个link之间的分隔符，默认值为：'##'
    '''
    netconnstr = ''
    def dumpConnections( node , connstr):
        "Helper function: dump connections to node"
        for intf in node.intfList():
            connstr += '%s' %intf + segment1
            if intf.link:
                intfs = [ intf.link.intf1, intf.link.intf2 ]
                intfs.remove( intf )
                connstr += '%s' % intfs[ 0 ] + segment2
#            else:
#                print ' '
        return connstr

    for node in nodes:
        netconnstr = dumpConnections( node , netconnstr)
    return netconnstr
#返回节点的下一跳的接口
def dumpNodenexthop( nodes ):
    '''
    作用：返回一个link的相对本个节点的下一跳的接口的名称
    但是一般不会这样使用，大多数情况下，传入的nodes是一个网络中所有节点的集合(不包括控制器)
    因此，使用这个方法就可以获取到一个包含网络中的所有的接口的名称(每个接口以##分隔)的字符串
    '''
    netconnstr = ''
    def dumpConnections( node , connstr):
        "Helper function: dump connections to node"
        for intf in node.intfList():
            if intf.link:
                intfs = [ intf.link.intf1, intf.link.intf2 ]
                intfs.remove( intf )
                connstr += '%s##' % intfs[ 0 ]
            else:
                print ' '
        return connstr

    for node in nodes:
        netconnstr = dumpConnections( node , netconnstr)
    return netconnstr
#输出除了控制器以外所有节点链路信息的方法
def dumpNetConnectionsWithoutController( net ):
    "Dump connections in network"
    '''
    与dumpNodesConnections结合使用
    只是在未知节点的情况下，传入mininet对象，获取整个网络中的所有的链路信息
    
    '''
    nodes = net.hosts + net.switches
    netconnstr = dumpNodeConnections( nodes )
    return netconnstr
#创建关键字的内容为列表的字典 
def addWord(theIndex,word,pagenumber): 
    '''
    传入三个参数：
    第一个:要存放数据的字典，类型是一个字典(dict)
    第二个:索引值也就是关键字,类型是一个字符串(str)
    第三个:每个索引值对应的内容，类型是一个列表(list)
    '''
    theIndex.setdefault(word, [ ]).append(pagenumber)
#一般用在存放的内容不是很多的情况   
def addWordstr(theIndex,word,pagenumber): 
    '''
    传入三个参数：
    第一个:要存放数据的字典,类型是dict
    第二个:索引值也就是关键字,类型是str
    第三个:每个索引值对应的内容,类型是str
    '''
    theIndex.setdefault(word, pagenumber)
    
#把获取的链路信息以一定的格式打印   
def DisplayInfoOfNet(network):
    '''
    这个方法的适用范围很广
    显示也很人性化，特别添加了ip的显示
    方便用户知道网络的整个信息
    只有一个参数：mininet网络对象
    返回值是一个关键字为ip的字典
    '''
    print '\n这个网络的信息如下:\n'
    hosts = network.hosts
    dumpNetConnections(network)#注意这个里的这个方法会把网络的信息打印出来，是来自mininet的方法
    host = {}
    subip = ''
    for i in range(0,4):
        ip = str(hosts[i].IP)
        j = ip.find('10.0.0.')
        for j in range(j,j+8):
            subip += ip[j]
        print hosts[i].name+' : '+subip
        addWord(host,subip,hosts[i].name)
        subip = ''
    return host
#获取网络节点的ip或姓名
def getnodeNAMEIP(lists,returntype='str',key='',element='',segment=''):
    '''
        这个方法必须在mininet的环境下适用，但是只要在mininet的环境下就可以任意调用
        有四个可选参数：如果你什么都不填写，只传进来一个参数(某个节点)，那么返回它的ip
        lists的类型既可以是host/switch对象,也可以是hosts/switches对象列表
        返回的参数有两类:IP字符串和IP列表
        returntype的默认值是str,返回的类型一般只有两种，另外一种是dict
        key:字典的关键字，key的默认值是为host,可以指定为ip
        element:列表的元素，element的默认值是ip,可以指定为name
        segment 表示在str中两个元素的分隔，segment的默认值为''
    '''
    def ListsName():
        listsname = ''
        for l in lists:
            listsname += l.name+segment
        return listsname
    return {'str':lambda :lists.IP(),#获取单个主机的ip
     'strname':lambda :ListsName(),#获取的是列表里面所有主机的名字
     'list':lambda :[l.IP() for l in lists],
     'listname':lambda :[l.name for l in lists],
     'dictip':lambda :{l.IP():l.name for l in lists},
     'dict':lambda :{l.name:l.IP() for l in lists}
    }[returntype+key+element]()

#作用简单讲就是：
#取一个lists和字符串的交集或者取一个字符串里面的节点名与lists不重合的部分
def changeSTRintoLIST(lists,string ='',select = 'notnone'):
    '''
    这个方法的使用是有限制的，如果你设置的select参数是notnone那么
    你传进来的字符串里面出现的节点名,它不会全部显示，只显示lists里面的，
    但是如果select参数值为none,就会返回在lists里面没有的主机名的一个列表
    第一个参数：网络设备的集合(交换机,主机)
    第二个参数：包含主机名称的字符串
    第三个参数：默认值为netnone，表示选择字符串中存在的名称
    '''
    selectedlist = []
    unselectedlist = []
    for l in lists:
        if(re.search(l.name,string) is not None):
            selectedlist.append(l)
        else:
            unselectedlist.append(l)
    return {'none':lambda:unselectedlist,
     'notnone':lambda:selectedlist}[select]()
    
#把网络的链接信息转化成列表
def ChangeNetConnintoLinkList(network):
    '''
    使用的参数很简单，就一个mininet的网络对象
    它这个函数的作用就是把从dumpNetConnectionsWithoutController()这个方法获取的
    一个网络链路信息的一个字符串进行处理，除去lo字符，##字符
    把所有的link都加入到一个列表里面，一个link就相当于列表的一项
    '''
    netconn = dumpNetConnectionsWithoutController(network)
    netconnlist = netconn.split('lo')
    netconn = ''
    for i in range(0,len(netconnlist)):
        netconn += netconnlist[i]
    netconnlist = netconn.split('##')
    return netconnlist
#判断ip的输入
def judgeIP(ip):
    '''
    这是一个判断ip的死方法，有待改进........
    '''
    result = True
    if(len(ip)<7 or len(ip)>15 or int(ip[0]) == 0):
        print '\nGrammar error'
    elif(len(ip)==8 and ip.find('10.0.0.')==0
        and int(ip[7])>0 and int(ip[7])<5):
            result = False
    else:
        print '\nip should between 10.0.0.1 and 10.0.0.4'
    return result
#把每个交换机的流表都输出到文件里面    
def DumpFlowstoFiles(switches):
    '''
    参数只有一个交换机对象的列表
    适用范围很广只要在mininet下面都可以使用
    '''
    print '\n\nAfter ping we dump flows into files\n'
    outfiles = {}
    for s in switches:
        outfiles[s] = '%s.out' % s.name
        s.cmd( 'echo >', outfiles[ s ] )
        s.cmdPrint('ovs-ofctl dump-flows',s.name,
                       '>', outfiles[ s ],
                       )
  
#这是一个找到交换机流表里面出现98个字节报文的转发，并出现两次的交换机的方法                    
def CheckSwitchFlowBy98bytes(switch):
    '''
    适用范围很广，可以sdn网络架构的所有流表
    只传入一个参数：交换机的对象，注意这里的传入的参数不是一个交换机对象的列表
    这个方法的原理很简单：就是看看哪个交换机的流表里面出现过两次98字节数据包的转发
    如果是就把它添加到一个字符串path里面，返回值就是path
    '''
    n_bytes = 'n_bytes='
    n_packets = 'n_packets='
    path = ''
    s = open(switch+'.out')
    flows = s.read()
    print "\nThis is flows of "+switch+'\n'
    print flows
    i = 0
    j = 0
    count = 0
    pstr = ''
    bstr = ''
    while(True):
        pstr = ''
        bstr = ''
        i = flows.find(n_packets,i,len(flows)-1)
        if(i<0):
            break
        j = flows.find(',',i,len(flows)-1)
        for k in range(i+len(n_packets),j):
            pstr += flows[k]
        i = flows.find(n_bytes,j,len(flows)-1)
        j = flows.find(',',i,len(flows)-1)
        for k in range(i+len(n_bytes),j):
            bstr += flows[k]
        ipstr = int(pstr)
        if(ipstr == 0):
            continue
        ibstr = int(bstr)
        if(ibstr/ipstr==98):
            count +=1
            if(count == 2):
                path += switch+'->'
                break
    s.close()
    return path
    
#这个方法就是把从源主机到目的主机的完整路线打印出来
def PathSrctoDst(path,network,srchostname,dsthostname,segment='->'):
    '''
    适用性一点都不好，只能在这个程序使用，后面有待改进........
    这个函数使用的参数对格式的要求很高：
    path字符串里面必须包含源主机名称且必须放在第一个
    '''
    netconnlist = ChangeNetConnintoLinkList(network)
    pathlist = path.split(segment)
    selectedswitch = ''
    for i in range(1,len(pathlist)-1):
        selectedswitch += pathlist[i]
        templist = pathlist
        for i in range(0,len(pathlist)-2):
            for j in range(0,len(netconnlist)-1):
                connect = netconnlist[j]
                if(connect.find(pathlist[i],0,2)==0):
                    connectsuffix = connect[7]+connect[8]
                    temp = ''
                    for k in range(0,i+1):
                        temp += templist[k]
                    if(temp.find(connectsuffix) == -1):
                        if(selectedswitch.find(connectsuffix) != -1):
                            templist[i+1] = connectsuffix
                            break
        pathlist = templist
        path = srchostname +'->'
        for i in range(1,len(pathlist)-1):
            path += pathlist[i] + '->'
        path += dsthostname
    return path

#使用action参数判断删除还是添加流表或者添加什么类型的流表
def StaticFlows(switches,action,**param):
    '''
        这个方法的开放性不好，还需要修改......
        
        使用了交换机对象的intfList属性,
        获取每个交换机的接口数量和端口号,
        动态的给每个交换机的每个接口添加或者删除静态流表
        传入三个参数：
        第一个：交换机列表，类型是list
        第二个：动作，如addarpflow,delarpflow,addstaticflow,delstaticflow
        第三个：额外的参数，可选可不选，在这个方法里面一般用来传递链路信息
        第四个和第五个分别是源ip和目的ip
        最后一个是一个字符串,可以省略
    '''  
    def addarpFlow(name,in_port,dpid):
        flow = {"switch":dpid,
                "actions":"output=flood",
                "priority":"32768",
                "name":name,
                "in_port":in_port,
                "eth_type":"0x0806",
                "active":"true"
        }
        return flow
    def delFlow(name):
        flow = {"name":name}
        return flow
    def addstaticFlow(dpid,output,name,in_port,ip):
        flow = {"switch":dpid,
                "actions":"output="+output,
                "priority":"32768",
                "name":name,
                "in_port":in_port,
                "eth_type":"0x0800",
                "nw_proto":"1",
                "ipv4_src":ip[0]+"/32",
                "ipv4_dst":ip[1]+"/32",
                "active":"true"
        }
        return flow
        
    for s in switches:
        intflist = s.intfList()#一个交换机的接口列表的长度
        intflistlen = len(intflist)
        #dpid一般是有16个字符组成的字符串，比如0000000000000001
        dpid = ''#存储的字符串形式为00:00:00:00:00:00:00:01
        dpidlen = len(s.dpid)
        #下面这个for循环就是为了dpid变成00:00:00:00:00:00:00:01的形式
        i = 0
        while(i<dpidlen-3):
            dpid += s.dpid[i:i+2]#取s.dpid的i~i+1个字符
            dpid += ':'
            i += 2
        dpid += s.dpid[dpidlen-2:dpidlen]
        #下面这个for循环作用是生成流表并推送流表到控制器,
        for j in range(1,intflistlen):#因为intflist的第一个元素为'lo',所以从1开始
            #这里需要注意的是intflistlen的长度不包括'lo'因此intflistlen不必减1
            sfp = StaticFlowPusher('127.0.0.1')#一个静态流表推送器
            intf = intflist[j].name
            in_port = intf[len(intf)-1]#类型是str
            name = s.name + '-' + in_port#流表的名称，是每个流表的唯一标识
            if(action.find('addarpflow')==0):
                flow = addarpFlow(name,in_port,dpid)
#                print '\n'
#                print flow
                sfp.set(flow)  
            elif(action.find('delarpflow')==0):
                flow = delFlow(name)
                sfp.remove(flow)
            elif(action.find('addstaticflow')==0):
                try:
                    intfdict = param['in_outputdict']
                    output = intfdict[intf][0]
                    name = param['hostspath']+'_'+name
                    ip = intfdict[intf][1]
                    flow = addstaticFlow(dpid,output,name,in_port,ip)
                    print flow
                    sfp.set(flow)
                except:
                    pass
            elif(action.find('delstaticflow')==0):
                try:
                    intfdict = param['in_outputdict']
                    output = intfdict[intf][0]
                    name = param['hostspath']+'_'+name
                    ip = intfdict[intf][1]
                    flow = delFlow(name)
#                    print '\n'
#                    print flow
                    sfp.remove(flow)
                except:
                    pass
#生成一个交换机入口和出口的字典              
def in_portOutputIp(switches,hosts,switchespath,hostspath):
    '''
    获取in_port与output的对应关系
    返回值为字典,源ip,目的ip
    注意：这里的switchespath是经过的交换机的路径是一个字符串，格式的要求不高
    只要出现交换机的名字就行了，不管出现其他的符号
    hostspath，这个字符串中只能出现两个主机的名称，分别是源主机和目的主机，
    出现其他符号也没有关系,必须源主机在目的主机的前面
    注意：hostspath字符串的第1个字符开始必须是源主机
    '''
    selectedhosts = changeSTRintoLIST(hosts,hostspath)#源主机和目的主机的列表
    srcname,dstname = getnodeNAMEIP(selectedhosts,returntype='list',element='name')#源主机与目的主机
    if(hostspath.find(srcname)!=0):
        tempname = srcname
        srcname = dstname
        dstname = tempname
    selectedswitches = changeSTRintoLIST(switches,switchespath)#经过的交换机
    selectedlist = changeSTRintoLIST(hosts + switches,hostspath+switchespath)
    sintf = dumpNodenexthop(selectedlist).split('##')#已选择的节点的下一跳的接口信息
    tempsintf = []#临时存放数据
    ssname = getnodeNAMEIP(selectedswitches,element='name')#经过的交换机的名字
    for intf in sintf:
        if(re.search(intf[0:2],ssname) is not None and len(intf)>0):
            tempsintf.append(intf)
    sintf = tempsintf
    sintfdict = {}#获取output与in_port的对应关系
    for intf in sintf:
        for iintf in sintf:
            if(iintf.find(intf[0:len(intf)-1])==0 and iintf.find(intf)<0):
                addWord(sintfdict,intf,iintf[len(intf)-1])
    return sintfdict,srcname,dstname
    
#程序的作用就是输出源主机到目的主机的接口的路径（intf）             
def srctodstlink(nodes,srcname,dstname,switchpath):
    '''
    这里对参数进行如下解释：
    nodes 表示的是网络中所有节点的对象的列表（除了控制器以外）
    swicthpath表示的是交换机的路径，格式要求不高，可以出现其他的符号
    但是要经过的交换机必须全部出现，而且交换机的顺序必须是从源主机到目的主机
    srcname 表示源主机的名字，不允许出现其他符号
    dstname 表示目的主机的名字，要求同srcname
    返回参数只有一个就是源主机到目的主机的接口的路径
    返回值的格式一般如下：
    #H1-eth0:s1-eth1#s1-eth4:s3-eth1#s3-eth2:s4-eth2#s4-eth4:D2-eth0
    下面代码行的注释是为了调试用的，如果出现未知的错误可以取消注释进行调试
    '''
    alllinks = dumpNodeConnections(nodes,':','lo:').split('lo:')
    switches = changeSTRintoLIST(nodes,switchpath)
    switchesname = getnodeNAMEIP(switches,element='name',segment=':')
    nodesname = switchesname+dstname#除了源主机名以外的所有节点的名字
    path = ''
    for link in alllinks:
        if(link.find(srcname)==0):
            path += '#'+link
#    print '当前生成的路径为：'+path+'\n'
    basic = 0
    tail = 0
    for i in range(0,len(switches)):
        basic = path.find('#',tail)
#        print 'basic='+ str(basic)
        head = path.find(':',basic)+1
#        print 'head='+str(head)
        tail = path.find('-',head)
#        print 'tail='+str(tail)
        nextname = path[head:tail]
#        print '选择的下一个开始的link的前面两个字符应该是：'+nextname+'\n'
        for link in alllinks:
            if(link.find(nextname) == 0):
#                print '前面两个字符匹配成功的link:'+link+'\n'
                linkhead = link.find(':')+1
                nextnextname = link[linkhead:link.find('-',linkhead)]
#                print '在link里面选择的下一个交换机的名字：'+nextnextname+'\n'
                if(nodesname.find(nextnextname)!=-1
                    and re.search(nextnextname,path) is None):
#                        print nextnextname +'：匹配成功\n'
                        path += '#'+link
#                        print '当前生成的路径为：'+path+'\n'
#                else:
#                    print nextnextname +'：匹配失败\n'
    return path                  
                
def ProcessControl():
    print '回车继续'
    while(True):
        click = raw_input()
        if(click.find('go on')==0):
            time.sleep(1)
        else:
            break
#启动floodlight        
def StartFloodLight(properties=''):
    '''
    函数调用的位置说明：
    必须在主程序一开始调用，不能放在mininet等方法的后面，否则会出错
    这是一个启动floodlight的方法，
    它只有一个参数配置文件——properties,
    properties的类型就是一个字符串，但是它的格式有严格的要求
    ' -cf '+它的绝对路径+文件名
    还有配置文件的参数可以不输入，默认调用floodlight的默认配置文件
    ！！！！！！注意：-cf 的前后有两个空格不能少
    返回值：当前floodlight运行的进程号，是一个int类型的值
    ！！！！！！注意：如果你在使用这个方法的时候始终都出现：
    你输入的floodlight的路径不存在，请重新输入！！！这个提示那么有可能是因为：
    floodlight的改动太大了以至于目录的结构都改变了，那么你只要做一点点修改：
    'target/bin/org/sdnplatform/sync'把这段字符换成你对应的floodlight的文件目录
    不必要求一模一样，只有你输入的路径足够长不会引起歧义就行了
    '''
    homedir = os.getcwd() + '/'
    ihomedir = 0
    for i in range(0,3):
        ihomedir = homedir.find('/',ihomedir)
        ihomedir +=1
    homedirdir = homedir[0:ihomedir]
    while(True):
        floodlightdir = ''
        if(os.path.exists(homedir+'floodlight.locate') is False):
            floodlightdir = raw_input('请输入floodlight的绝对路径：'+homedirdir)
            os.system('echo '+floodlightdir+' > floodlight.locate')
        else:
            f = open('floodlight.locate','r')
            floodlightdir = str(f.read())
            floodlightdir = floodlightdir[:len(floodlightdir)-1]
            f.close()
        tempdir = homedirdir+floodlightdir
        floodlightdir = tempdir
        if(floodlightdir[len(floodlightdir)-1].find('/')==0):
            tempdir += 'target/bin/org/sdnplatform/sync'
        else:
            tempdir += '/target/bin/org/sdnplatform/sync'
            floodlightdir +='/'
        if(os.path.exists(tempdir)):
            break
        else:
            print '你输入的floodlight的路径不存在，请重新输入！！！'
            os.system('sudo rm -rf floodlight.locate')
    jcommand = '\njava -jar target/floodlight.jar'+properties
    command = " 'cd "+floodlightdir+jcommand+"'"
    os.system('echo'+command+' > startfloolight.sh')
    progress = os.system('xterm -e bash startfloolight.sh &')
    print 'floodlight控制器正在启动中，请稍等片刻......'
    time.sleep(4)
    return progress
#关闭floodlight
def StopFloodLight(progress):
    '''
    这是一个关闭floodlight的方法
    它只有一个参数:进程号——process
    注意:process这个参数是外面传进来的
    不要忘记填写否则会出错
    '''
    os.system('kill '+str(progress))
    
#删除交换机所有的流表
def DelSwitchAllFlows(switches):
    for s in switches:
        s.cmd('ovs-ofctl del-flows '+s.name)
        