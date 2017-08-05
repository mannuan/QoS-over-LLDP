# -*- coding: utf-8 -*-
"""
Created on Fri Apr 14 22:55:50 2017

@author: mininet
"""
import os,glob,sys,time
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
    '''
#    homedir = os.path.expanduser('~')#当前用户的主目录
    cwddir = os.getcwd()#当前路径
    homedir = '/'+cwddir.split('/')[1]+'/'+cwddir.split('/')[2]
    prompt = '请重新输入!!!'#提示
    floodlightdir = None
    if os.path.exists(cwddir+'/.floodlightdir'):#如果之前已经使用了floodlight
        f = open('.floodlightdir')
        floodlightdir = f.read()[:-1]
        f.close()
        print '当前使用的floodlight的路径是:'+homedir+'/'+floodlightdir
    while(True):
        if floodlightdir is None:#如果floodlightdir为空
            floodlightdir = raw_input('请输入floodlight的路径:'+homedir+'/')
        try:
            os.chdir(homedir+'/'+floodlightdir)#切换到floodlight的目录
            if len(glob.glob('floodlight.sh')) is 0:#如果这不是floodlight的目录
                floodlightdir = None
                print '这不是floodlight的目录'+prompt
            else:#如果这是floodlight的目录
                if len(glob.glob(r'*target*')) is 0:#如果floodlight没有编译
                    print 'floodlight没有编译,即将自动编译'
                    time.sleep(1)
                    os.system('ant')
                    print '编译完成,即将启动floodlight'
                    break
                else:#如果floodlight已经编译了
                    break
        except Exception:
            floodlightdir = None
            print '路径不存在'+prompt
    os.chdir(cwddir)#切换回之前的目录
    os.system('echo '+floodlightdir+' > .floodlightdir')#保存floodlight的路径
    #运行floodlight程序
    cmd='ps ax | grep \'java -jar target/floodlight.jar\' | sed \'/grep/d\' | awk \'{print $1}\''
    process = os.popen(cmd).read()[:-1]#获取floodlight的进程号
    if(len(process) is not 0):#如果floodlight没开
        os.system('kill '+process)
    os.chdir(homedir+'/'+floodlightdir)#切换到floodlight的目录
    os.system('java -jar target/floodlight.jar')
#    for i in range(11):
#        sys.stdout.write('  floodlight启动中,剩余%.2ds\r'%(10-i))
#        sys.stdout.flush()
#        time.sleep(1)
#    print 'floodlight控制器初始化完毕'
#    os.chdir(homedir)#为了方便使用把当前活动目录切换到home目录        

#关闭floodlight
def StopFloodLight():
    '''
    这是一个关闭floodlight的方法
    '''
    cmd='ps ax | grep \'java -jar target/floodlight.jar\' | sed \'/grep/d\' | awk \'{print $1}\''
    process = os.popen(cmd).read()[:-1]#获取floodlight的进程号
    os.system('kill '+process)
    print cmd