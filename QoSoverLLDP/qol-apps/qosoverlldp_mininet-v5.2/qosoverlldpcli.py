# -*- coding: utf-8 -*-
"""
Created on Fri Apr 14 22:52:18 2017

@author: mininet
"""
import re,subprocess,os,json,urllib2
from mininet.cli import CLI
from mininet.log import info

class QoSoverLLDPCLI(CLI):         
    def do_tcadd(self, line):
        """Run an external shell command
           Usage: change [cmd args]"""
        assert self  # satisfy pylint and allow override
        prompt = \
        'usage: DEV SELECTION [VALUE]\n'+\
        'where  SELECTION := { help }\n'+\
        '       DEV := s?-DEV? eg:s1-DEV1\n'+\
        '       VLAUE := [ ?bit | ?Kbit | ?Mbit | ?Gbit ]\n'+\
        'e.g:   tcadd help'+\
        '       tcadd s1-eth3 10Mbit 12000  1000 0.01'
        '       四个值分别是带宽、时延、抖动、丢包率'
        try:
            arglist = line.split()
            result = ''
            if len(arglist) is 0:
                print prompt
            elif len(re.findall(r'help',arglist[0])) is 1:
                print prompt
            else:
                result=\
                'tc qdisc add dev '+str(arglist[0])+' root handle 5:0 htb default 1\n'+\
                'tc class add dev '+str(arglist[0])+' parent 5:0 classid 5:1 htb rate '+str(arglist[1])+' burst 15k\n'+\
                'tc qdisc add dev ' + str(arglist[0]) + ' parent 5:1 handle 10: netem delay '+str(arglist[2])+'  '+str(arglist[3])+' loss '+str(arglist[4])
                print result
            subprocess.call( result, shell=True )
        except Exception,e:
            print e
        
    def do_tchange( self, line ):
        """Run an external shell command
           Usage: change [cmd args]"""
        assert self  # satisfy pylint and allow override
        prompt = \
        'usage: DEV SELECTION [VALUE]\n'+\
        'where  SELECTION := { bandwidth | delay | jitter | loss | help }\n'+\
        '       DEV := s?-DEV? eg:s1-DEV1\n'+\
        '       VLAUE := [ ?bit | ?Kbit | ?Mbit | ?Gbit ]\n'+\
        'e.g:   tchange s1-eth3 bandwidth 12Mbit'+\
        '       tchange s1-eth3 delay 12000'+\
        '       tchange s1-eth3 jitter 12000'+\
        '       tchange s1-eth4 loss 1'
        def get_delay_jitter_loss(dev):
            #delay
            delay = os.popen('tc qdisc show dev '+dev+' | '+\
                'grep \'delay\' | sed \'s/^.*delay //g\' | '+\
                'sed \'s/ .*$//g\'').read()
            delay = delay.replace('\n','')
            if delay.find('u')!=-1 or delay.find('m')!=-1:
                unit = delay[len(delay)-2:len(delay)]
                delay = delay[:len(delay)-2]
                delay = str(float(delay)+1)+unit
            if(len(delay) is 0):
                delay = None
            #jitter
            jitter = os.popen('tc qdisc show dev '+dev+' | '+\
                'grep \'  \' | sed \'s/^.*  //g\' | '+\
                'sed \'s/ .*$//g\'').read()
            jitter = jitter.replace('\n','')
            if jitter.find('u')!=-1 or jitter.find('m')!=-1:
                unit = jitter[len(jitter)-2:len(jitter)]
                jitter = jitter[:len(jitter)-2]
                jitter = str(float(jitter)+1)+unit
            if(len(jitter) is 0):
                jitter = None
            #loss
            loss = os.popen('tc qdisc show dev '+dev+' | '+\
                'grep \'loss\' | sed \'s/^.*loss //g\' | '+\
                'sed \'s/ .*$//g\'').read()
            loss = loss.replace('\n','')
            if(len(loss) is 0):
                loss = None
            return delay,jitter,loss
        try:
            arglist = line.split()
            result = ''
            if len(arglist) is 0:
                print prompt
            elif len(re.findall(r'help',arglist[0])) is 1:
                print prompt
            elif len(re.findall(r'bandwidth',arglist[1])) is 1:
                if not arglist[2].isdigit():
                    result = 'tc class change dev ' + str(arglist[0]) + ' parent 5:0 classid 5:1 htb rate '\
                    + str(arglist[2]) + ' burst 15k'
                else:
                    print 'unit:Mbit,bit'
                print result
            elif len(re.findall(r'delay',arglist[1])) is 1:
                if float(arglist[2])>=2:
                    delay,jitter,loss = get_delay_jitter_loss(str(arglist[0]))
                    result = 'tc qdisc change dev ' + str(arglist[0]) + ' parent 5:1 handle 10: netem '+\
                    'delay '+str(arglist[2])+'  '+jitter+' loss '+loss
                else:
                    print 'delay too small'
                print result
            elif(len(re.findall(r'jitter',arglist[1])) == 1):
                if float(arglist[2])>=2:
                    delay,jitter,loss = get_delay_jitter_loss(str(arglist[0]))
                    result = 'tc qdisc change dev ' + str(arglist[0]) + ' parent 5:1 handle 10: netem '+\
                    'delay '+delay+'  '+str(arglist[2])+' loss '+loss
                else:
                    print 'jitter too small'
                print result
            elif len(re.findall(r'loss',arglist[1])) is 1:
                if float(arglist[2])>=0.0001:
                    delay,jitter,loss = get_delay_jitter_loss(str(arglist[0]))
                    result = 'tc qdisc change dev ' + str(arglist[0]) + ' parent 5:1 handle 10: netem '+\
                    'delay '+delay+'  '+jitter+' loss '+str(arglist[2])
                else:
                    print 'loss too small'
                print result
            else:
                print 'unknown error'
            subprocess.call( result, shell=True )
        except Exception,e:
            print e
            
    def do_addStaticEntry(self, line):
            
        prompt = 'usage: addStaticEntry optimal_type src dst\n'+\
                 'e.g:   addStaticEntry bandwidth h1 h2\n'+\
                 '       addStaticEntry delay h1 h2\n'+\
                 '       addStaticEntry jitter h1 h2\n'+\
                 '       addStaticEntry loss h1 h2\n'+\
                 '       addStaticEntry latency h1 h2\n'+\
                 '       addStaticEntry total h1 h2\n'
                 
        try:
            arglist = line.split()
            if len(arglist) is 0:
                print prompt
            elif len(re.findall(r'help',arglist[0])) is 1:
                print prompt
            elif len(arglist) is 3:
                optimal_type = arglist[0]
                src = arglist[1]
                dst = arglist[2]
                url = 'http://localhost:8080/wm/qosoverlldp/staticentrypusher/'+optimal_type+'/'+src+'/'+dst+'/json'
                print json.loads(urllib2.urlopen(url).read())
            else:
                print prompt
        except Exception,e:
            print e
            
    def do_reset( self, line ):
        """Run reset shell command
           Usage: reset"""
        assert self  # satisfy pylint and allow override
        subprocess.call( 'reset', shell=True )
        
    def do_changeInterval(self, line):#改变获取动态qos信息的时间间隔
    
        prompt = 'usage: changeInterval interval\n'+\
                 'e.g:   changeInterval 1.5\n'+\
                 '       changeInterval 1\n'
        try:  
            arglist = line.split()
            if len(arglist) is 0:
                print prompt
            elif len(re.findall(r'help',arglist[0])) is 1:
                print prompt
            else:
                f = open('/tmp/dynamic-qos_qosoverlldp/dynamic-qos.sh','r')
                script = f.read()
                f.close()
                script = script[:script.find('INTERVAL=')+len('INTERVAL=')]+'%f'+script[script.find('\nDEV=$1'):]
                script=script%float(arglist[0])
                f = open('/tmp/dynamic-qos_qosoverlldp/dynamic-qos.sh','w')#覆盖式的写入数据
                f.write(script)
                f.close()
                #把每个网络接口对应的独立进程关闭
                cmd='ps ax | grep \'sh /tmp/dynamic-qos_qosoverlldp/dynamic-qos.sh\' | sed \'/grep/d\' | awk \'{print $1}\' | xargs'
                processarr = os.popen(cmd).read()[:-1].split(' ')#获取每个网络接口上的进程号的数组
                for i in range(0,len(processarr)):
                    info('kill '+processarr[i]+'  ')
                    os.system('kill '+processarr[i])#关闭每个网络接口上面的进程
                info('\n')
        except Exception,e:
            print e