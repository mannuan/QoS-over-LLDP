# -*- coding: utf-8 -*-
"""
Created on Wed May  4 17:23:44 2016

@author: wjl
"""
import os,sys
from qosoverlldputil import StartFloodLight,StopFloodLight
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
    elif 'mesh=' in sys.argv[1]:#mesh
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
            if k < 2 or density < 0:
                print 'k必须大于等于2和density必须大于等于0!!!'
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
    elif 'custom=' in sys.argv[1]:#custom
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
        qosoverlldp_cmd = 'gnome-terminal -e \"python qosoverlldp.py '+topotype+' floodlight\"'
        print qosoverlldp_cmd
        os.system(qosoverlldp_cmd)
        StartFloodLight()#开启floodlight控制器
        StopFloodLight()#关闭floodlight控制器
        os.system('reset')
        sys.exit()
    except Exception,e:
        StopFloodLight()
        os.system('sudo mn -c')
        print e