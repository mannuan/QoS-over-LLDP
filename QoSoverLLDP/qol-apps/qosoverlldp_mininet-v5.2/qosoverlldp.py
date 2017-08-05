# -*- coding: utf-8 -*-
"""
Created on Sat Feb 25 14:31:06 2017

@author: wjl
"""
import os,time,sys,random,logging
from xml.etree import ElementTree
import numpy as np
from scipy import stats
from mininet.log import info, error, debug, output, warn
#import os.path as op
#sys.path.append(op.expanduser('~')+"/mininet/mininet")
from mininet.net import Mininet
from mininet.node import RemoteController
from mininet.link import TCLink,TCIntf
from mininet.log import setLogLevel
from mininet.topolib import TreeTopo
from qosoverlldpcli import QoSoverLLDPCLI
from qosoverlldputil import StopFloodLight
from topo_mesh import MeshTopo
from topo_ring import RingTopo
from topo_fattree import Fattree
from topo_templet import TempletTopo

class QoSoverLLDP( object ):
    def __init__( self ,topotype='templet'):
#        sw_count * link_count_per_sw must be even
#        交换机与每个交换机的链路的乘积必须是一个偶数
        sw_count=4
        link_count_per_sw=3
        host_count=4
        host_count_per_sw=1
        self.server = "127.0.0.1"
        topo = TempletTopo()#要创建的拓扑
        if 'mesh' in topotype:
            topotype = topotype[topotype .index('=')+1:len(topotype)]
            arr = topotype.split(',')
            sw_count=int(arr[0])
            link_count_per_sw=int(arr[1])
            host_count=int(arr[2])
            host_count_per_sw=int(arr[3])
            topo = MeshTopo(sw_count=sw_count,link_count_per_sw=link_count_per_sw,
                                host_count=host_count,host_count_per_sw=host_count_per_sw)
        elif 'ring' in topotype:
            topotype = topotype[topotype.index('=')+1:len(topotype)]
            arr = topotype.split(',')
            ringlength=int(arr[0])
            hostnum=int(arr[1])
            topo = RingTopo(ringlength=ringlength,hostnum=hostnum)
        elif 'fattree' in topotype:
            topotype = topotype[topotype.index('=')+1:len(topotype)]
            arr = topotype.split(',')
            k = int(arr[0])
            density = int(arr[1])
            topo = Fattree(k, density)
            topo.createTopo()
            topo.createLink(bw_c2a=0.2, bw_a2e=0.1, bw_h2a=0.05)
        elif 'tree' in topotype:
            topotype = topotype[topotype.index('=')+1:len(topotype)]
            arr = topotype.split(',')
            treedepth=int(arr[0])
            treefanout=int(arr[1])
            topo = TreeTopo()
            topo.build(treedepth,treefanout)
        elif 'custom' in topotype:
            topotype = topotype[topotype.index('=')+1:len(topotype)]
            arr = topotype.split(',')
            exec 'from %s import %s'%(py,module)
            exec 'topo=%s()'%(module)
        setLogLevel('info')#开启日志功能
        self.network = Mininet(topo = topo,controller = RemoteController,autoSetMacs=True,
             link=TCLink,intf=TCIntf)#要创建的网络
        self.network.addController('qosoverlldp')#加入控制器qosoverlldp
    
    def start( self ):
        #开启sdn网络
        self.network.start()
        #初始化网络里面的交换机的每个接口的qos信息,并为每个网络接口开启独立的获取剩余带宽和丢包率的独立进程
        #读取配置文件
        selection = ElementTree.parse("qosoverlldp.xml")
        #载入数据到字典
        qos_init = selection.getiterator("qos-init")[0]
        pattern_dict = dict()
        for pattern in qos_init:
            p_dict = dict()
            for p in pattern:
                p_dict.setdefault(p.tag,p.text)
            pattern_dict.setdefault(pattern.tag,p_dict)
        #筛选出生效的数据
        for p in pattern_dict.keys():
            if pattern_dict[p]["status"] in "false":
                pattern_dict.pop(p)
        #如果没有一个数据生效则自动生成数据
#        if len(pattern_dict) is 0:
#            pattern_dict.setdefault("even",{"bandwidth":"100Mbit","delay":"900us","jitter":"100us","loss":"0.001%"})
        #格式化数据
        print pattern_dict
        if len(pattern_dict) > 0:
            pattern_key,pattern_value = pattern_dict.items()[0]
            for k in pattern_value.keys():
                if "Gbit" in pattern_value[k]:
                    v = pattern_value[k]
                    pattern_value[k] = float(v[:len(v)-len("Gbit")])*1000
                elif "Mbit" in pattern_value[k]:
                    v = pattern_value[k]
                    pattern_value[k] = float(v[:len(v)-len("Mbit")])
                elif "Kbit" in pattern_value[k]:
                    v = pattern_value[k]
                    pattern_value[k] = float(v[:len(v)-len("Kbit")])/1000
                elif "bit" in pattern_value[k]:
                    v = pattern_value[k]
                    pattern_value[k] = float(v[:len(v)-len("bit")])/1000000
                elif "us" in pattern_value[k]:
                    v = pattern_value[k]
                    pattern_value[k] = float(v[:len(v)-len("us")])
                elif "ms" in pattern_value[k]:
                    v = pattern_value[k]
                    pattern_value[k] = float(v[:len(v)-len("ms")])*1000
                elif "s" in pattern_value[k]:
                    v = pattern_value[k]
                    pattern_value[k] = float(v[:len(v)-len("s")])*1000000
                elif "%" in pattern_value[k]:
                    v = pattern_value[k]
                    pattern_value[k] = float(v[:len(v)-len("%")])
                elif pattern_value[k].isdigit():
                    v = pattern_value[k]
                    pattern_value[k] = float(v)
            #读取配置文件结束
            if pattern_dict.has_key("even"):
                for sw in self.network.switches:
                    for port in sw.ports:
                        if port.name is not "lo":
                            info(port.name)
                            port.config(bw=pattern_dict["even"]["bandwidth"],delay=pattern_dict["even"]["delay"],
                                        jitter=pattern_dict["even"]["jitter"],loss=pattern_dict["even"]["loss"])
                            info('\n')
            elif pattern_dict.has_key("random"):
                for sw in self.network.switches:
                    for port in sw.ports:
                        if port.name is not "lo":
                            info(port.name)
                            port.config(bw=random.randint(pattern_dict["random"]["min-bandwidth"], pattern_dict["random"]["max-bandwidth"]),
                                        delay=random.randint(pattern_dict["random"]["min-delay"], pattern_dict["random"]["max-delay"]),
                                        jitter=random.randint(pattern_dict["random"]["min-jitter"], pattern_dict["random"]["max-jitter"]),
                                        loss=random.randint(pattern_dict["random"]["min-loss"], pattern_dict["random"]["max-loss"]))
                            info('\n')
            elif pattern_dict.has_key("normal"):
                portlist=list()
                for sw in self.network.switches:
                    for port in sw.ports:
                        if port.name is not "lo":
                            portlist.append(port)
                mu = len(portlist)/2#均值
                sigma = 2#标准差
                x = np.arange(0,len(portlist)+4,1)
                y = list(stats.norm.pdf(x,mu,sigma))
                del y[0:2]
                del y[len(y)-2:len(y)]
                total=0
                for i in y:
                    total+=i
                for i in range(len(y)):
                    y[i]/=total
                bandwidthMax=pattern_dict["normal"]["max-bandwidth"]#单位Mbit
                delayMax=pattern_dict["normal"]["max-delay"]#单位us
                jitterMax=pattern_dict["normal"]["max-jitter"]#单位us
                lossMax=pattern_dict["normal"]["max-loss"]#单位%
                for i in range(len(portlist)):
                    info(portlist[i].name)
                    portlist[i].config(bw=(lambda x:1 if x<1 else x)(bandwidthMax*y[i]),
                        delay=(lambda x:1 if x<1 else x)(delayMax*y[i]),
                        jitter=(lambda x:1 if x<1 else x)(jitterMax*y[i]),
                        loss=(lambda x:0.0001 if x<0.0001 else x)(lossMax*y[i]))
                    info("\n")
           
        cwddir = os.getcwd()#当前路径
        homedir = '/'+cwddir.split('/')[1]+'/'+cwddir.split('/')[2]
        os.chdir(homedir)#为了方便切换到home目录
                    
    #命令行模式          
    def cli( self ):
        QoSoverLLDPCLI(self.network)
        
    #关闭sdn网络和相关清理工作         
    def stop( self ):
        #关闭sdn网络 
        self.network.stop()
        #清空创建的网络接口
        os.system('sudo mn -c')

if __name__ == '__main__':
    prompt = \
    'usage: sudo python debug.py [ topotype [selection] ]\n'+\
    'where  topotype = mesh=sw_count,link_count_per_sw,host_count,host_count_per_sw \n'+\
    'where  topotype = custom=pyname,modulename \n'+\
    'e.g:\n'+\
    '    2: sudo python qosoverlldp.py help\n'+\
    '    1: sudo python qosoverlldp.py\n'+\
    '       (表示:默认生成一个包含四个交换机、4个主机的一个典型拓扑)\n'+\
    '    3: sudo python qosoverlldp.py mesh=4,3,4,1'+\
    '       (表示:4个交换机、每个交换机3条链路、4个主机、每个交换机上面连接一个主机)\n'+\
    '    4: sudo python qosoverlldp.py ring=8,8\n'+\
    '       (表示:环上有8个交换机、8个主机、中间默认有一个主机)\n'+\
    '    5: sudo python qosoverlldp.py fattree=3,4\n'+\
    '       (表示:k、denisty)\n'+\
    '    6: sudo python qosoverlldp.py tree=3,4\n'+\
    '       (表示:树的深度为4、每个结点的出度为4)\n'+\
    '    7: sudo python qosoverlldp.py custom=mytopo,MyTopo\n'+\
    '       (表示:第一个参数为文件名（不包含后缀.py），第二个为类名)\n'+\
    '    note: 必须是以上几种格式\n'
    
    topotype = 'templet'
    if len(sys.argv) is 1:#直接允许程序
        pass
    elif sys.argv[1] in 'help':#显示帮助信息
        print prompt
        sys.exit(0)
    elif 'mesh' in sys.argv[1]:#mesh
        argv1 = sys.argv[1]
        argv1 = argv1[argv1.index('=')+1:len(argv1)]
        try:
            arr = argv1.split(',')
            sw_count=int(arr[0])
            link_count_per_sw=int(arr[1])
            host_count=int(arr[2])
            host_count_per_sw=int(arr[3])
            if sw_count is 0 or link_count_per_sw is 0 or host_count is 0 or host_count_per_sw is 0:
                print '不可以为0!!!'
                sys.exit()
            elif sw_count*link_count_per_sw%2 is not 0:#如果不是偶数
                print '交换机的数量与交换机链路的数量的乘积必须是偶数!!!'
                sys.exit()
            elif sw_count < host_count/host_count_per_sw:
                print '交换机的数量必须大于主机的数量除以每个交换机上面链接主机的数量!!!'
                sys.exit()
            topotype=sys.argv[1]
        except Exception,e:
            print e
            sys.exit()
    elif 'ring=' in sys.argv[1]:#ring
        argv1 = sys.argv[1]
        argv1 = argv1[argv1.index('=')+1:len(argv1)]
        try:
            arr = argv1.split(',')
            ringlength=int(arr[0])
            hostnum=int(arr[1])
            if ringlength < 3 or hostnum < 0:
                print '环上交换机的数量不小于3或者主机的数量大于0!!!'
                sys.exit()
            topotype=sys.argv[1]
        except Exception,e:
            print e
            sys.exit()
    elif 'fattree=' in sys.argv[1]:#fattree
        argv1 = sys.argv[1]
        argv1 = argv1[argv1.index('=')+1:len(argv1)]
        try:
            arr = argv1.split(',')
            k=int(arr[0])
            density=int(arr[1])
            if k < 0 or density < 0:
                print 'k和density必须大于0!!!'
                sys.exit()
            topotype=sys.argv[1]
        except Exception,e:
            print e
            sys.exit()
    elif 'tree=' in sys.argv[1]:#tree
        argv1 = sys.argv[1]
        argv1 = argv1[argv1.index('=')+1:len(argv1)]
        try:
            arr = argv1.split(',')
            treedepth=int(arr[0])
            treefanout=int(arr[1])
            if treedepth < 0 or treefanout < 0:
                print '树型拓扑的深度或出度必须大于大于等于1'
                sys.exit()
            topotype=sys.argv[1]
        except Exception,e:
            print e
            sys.exit()
    elif 'custom' in sys.argv[1]:#custom
        argv1 = sys.argv[1]
        argv1 = argv1[argv1.index('=')+1:len(argv1)]
        try:
            arr = argv1.split(',')
            py = arr[0]
            module = arr[1]
            exec 'from %s import %s'%(py,module)
            topotype=sys.argv[1]
        except Exception,e:
            print e
            sys.exit()
    elif sys.argv[1] in 'templet':
        pass
    else:#都不符合则显示帮助信息
        print '输入有错!!!'
        print prompt
        sys.exit(0)
    try:
        timer = 120
        if len(sys.argv) is 3:#先判断参数的数量，防止意外出错
            if sys.argv[2] in 'floodlight':#如果要开启floodlight
                while(True):
                    m, s = divmod(timer, 60)
                    h, m = divmod(m, 60)
                    sys.stdout.write(' 请在主进程输入floodlight路径,然后开启floodlight控制器,倒计时: %dmin %02ds\r'%(m, s))
                    sys.stdout.flush()
                    cmd='ps ax | grep \'java -jar target/floodlight.jar\' | sed \'/grep/d\' | awk \'{print $1}\''
                    process = os.popen(cmd).read()[:-1]#获取floodlight的进程号
                    timer-=1
                    if(len(process)>0):
                        break
                    elif(timer is 0-1):
                        cmd='ps ax | grep \'sudo python floodlight_qosoverlldp.py\' | sed \'/grep/d\' | awk \'{print $1}\''
                        process = os.popen(cmd).read()[:-1]
                        os.system('kill '+process)
                        break
                    time.sleep(1)
                for i in range(10):
                    sys.stdout.write('  floodlight启动中,剩余%ds                                               \r'
                        %(9-i))
                    sys.stdout.flush()
                    time.sleep(1)
                print 'floodlight控制器初始化完毕'
        #开始
        if(timer is not 0-1):
#            f = open("timelist.txt","a")
#            t1 = time.time()
            qol = QoSoverLLDP(topotype=topotype)
            qol.start()
            qol.cli()
            qol.stop()
#            t2 = time.time()
#            f.write("%f\n"%(t2-t1))
#            f.close()
            if len(sys.argv) is 3:
                if sys.argv[2] in 'floodlight':
                    StopFloodLight()#关闭floodlight控制器
    except Exception,e:
        StopFloodLight()
        os.system('sudo mn -c')
        logging.exception("exception")
        print e
