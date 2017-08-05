# -*- coding: utf-8 -*-
"""
Created on Fri May 27 19:29:32 2016

@author: wjl
"""

import re,os,time

from Tkinter import Frame, Button, Label, Text, Scrollbar, Canvas, Wm, READABLE,INSERT,StringVar

from mininet.log import setLogLevel
from mininet.term import makeTerms, cleanUpScreens
from mininet.util import quietRun
from mininet.node import RemoteController
from mininet.net import Mininet
from myutil_2 import (StartFloodLight,StopFloodLight,StaticFlows,DynamicOperateFlows,DumpFlowstoFiles,
                    CheckSwitchFlowBy98bytes,PathSrctoDst)
from forth_topo import MyTopo

class Console( Frame ):
    "A simple console on a host."

    def __init__( self, parent, net, node, height=8, width=30, title='Node' ):
        Frame.__init__( self, parent )

        self.net = net
        self.node = node
        self.prompt = ''
        self.height, self.width, self.title = height, width, title
        self.route = StringVar()
        self.route_boole = False
        self.routename = ''
        self.dstname = ''
        # Initialize widget styles
        self.labelStyle = { 
            'font': 'Monaco 8',
            'bg': 'white',
            'fg': 'black'
        }
        self.textStyle = {
            'font': 'Monaco 8',
            'bg': 'white',
            'fg': 'black',
            'width': self.width,
            'height': self.height,
            'relief': 'sunken',
            'insertbackground': 'green',
            'highlightcolor': 'green',
            'selectforeground': 'black',
            'selectbackground': 'green'
        }

        # Set up widgets
        self.text = self.makeWidgets( )
        self.bindEvents()
        self.sendCmd( 'export TERM=dumb' )

        self.outputHook = None

    def makeWidgets( self ):
        "Make a label, a text area, and a scroll bar."
        
        def operateflows():
            routename = self.routename
            if(self.route_boole):
                hostspath = routename[0:3]+routename[len(routename)-2:len(routename)]
                switchespath = routename[4:len(routename)-4]
                DynamicOperateFlows(network,hostspath,switchespath,action='delstaticflow')
                self.route.set('添加'+routename+'的流表')
                button['bg']='green'
                self.route_boole = False 
            else:
                hostspath = routename[0:3]+routename[len(routename)-2:len(routename)]
                switchespath = routename[4:len(routename)-4]
                DynamicOperateFlows(network,hostspath,switchespath,action='addstaticflow')
                self.route.set('删除'+routename+'的流表')
                button['bg']='red'
                self.route_boole = True
        
        if(self.node.name.find('H1')==0):
            self.destname = self.net.get('D1').name
            pingname = 'H1 ping D1'
            self.routename = 'H1->s1->s2->s4->D1'
        elif(self.node.name.find('H2')==0):
            self.destname = self.net.get('D2').name
            pingname = 'H2 ping D2'
            self.routename = 'H2->s1->s2->s4->D2'
        elif(self.node.name.find('D1')==0):
            self.destname = self.net.get('H2').name
            pingname = 'D1 ping H2'
            self.routename = 'H2->s1->s3->s4->D1'
        elif(self.node.name.find('D2')==0):
            self.destname = self.net.get('H1').name
            pingname = 'D2 ping H1'
            self.routename = 'H1->s1->s3->s4->D2'
        self.route.set('添加'+self.routename+'的流表')
        button = Button(self,textvariable=self.route,command = operateflows,bg='green',fg='white')
        button.pack(side='top',fill='x')
        label = Label( self, text=pingname,**self.labelStyle )
        label.pack( side='top', fill='x' )
        text = Text( self, wrap='word', **self.textStyle )
        ybar = Scrollbar( self, orient='vertical', width=5,
                          command=text.yview )
        text.configure( yscrollcommand=ybar.set )
        text.pack( side='left', expand=True, fill='both' )
        ybar.pack( side='right', fill='y' )
        return text

    def bindEvents( self ):
        "Bind keyboard and file events."
        # The text widget handles regular key presses, but we
        # use special handlers for the following:
        self.text.bind( '<Return>', self.handleReturn )
        self.text.bind( '<Control-c>', self.handleInt )
        self.text.bind( '<KeyPress>', self.handleKey )
        # This is not well-documented, but it is the correct
        # way to trigger a file event handler from Tk's
        # event loop!
        self.tk.createfilehandler( self.node.stdout, READABLE,
                                   self.handleReadable )

    # We're not a terminal (yet?), so we ignore the following
    # control characters other than [\b\n\r]
    ignoreChars = re.compile( r'[\x00-\x07\x09\x0b\x0c\x0e-\x1f]+' )

    def append( self, text ):
        "Append something to our text frame."
        text = self.ignoreChars.sub( '', text )
        self.text.insert( 'end', text )
        self.text.mark_set( 'insert', 'end' )
        self.text.see( 'insert' )
        outputHook = lambda x, y: True  # make pylint happier
        if self.outputHook:
            outputHook = self.outputHook
        outputHook( self, text )

    def handleKey( self, event ):
        "If it's an interactive command, send it to the node."
        char = event.char
        if self.node.waiting:
            self.node.write( char )

    def handleReturn( self, event ):
        "Handle a carriage return."
        cmd = self.text.get( 'insert linestart', 'insert lineend' )
        # Send it immediately, if "interactive" command
        if self.node.waiting:
            self.node.write( event.char )
            return
        # Otherwise send the whole line to the shell
        pos = cmd.find( self.prompt )
        if pos >= 0:
            cmd = cmd[ pos + len( self.prompt ): ]
        self.sendCmd( cmd )

    # Callback ignores event
    def handleInt( self, _event=None ):
        "Handle control-c."
        self.node.sendInt()

    def sendCmd( self, cmd ):
        "Send a command to our node."
        if not self.node.waiting:
            self.node.sendCmd( cmd )

    def handleReadable( self, _fds, timeoutms=None ):
        "Handle file readable event."
        data = self.node.monitor( timeoutms )
        self.append( data )
        if not self.node.waiting:
            # Print prompt
            self.append( self.prompt )

    def waiting( self ):
        "Are we waiting for output?"
        return self.node.waiting

    def waitOutput( self ):
        "Wait for any remaining output."
        while self.node.waiting:
            # A bit of a trade-off here...
            self.handleReadable( self, timeoutms=1000)
            self.update()

    def clear( self ):
        "Clear all of our text."
        self.text.delete( '1.0', 'end' )
        
class ConsoleApp( Frame ):

    "Simple Tk consoles for Mininet."

    menuStyle = { 
        'font': 'Geneva 8 bold',
        'width': 20
    }
    frameStyle = { 
        'height':2 
    }

    def __init__( self, net, parent=None, width=4 ):
        Frame.__init__( self, parent )
        self.top = self.winfo_toplevel()
        self.top.title( '路由控制应用' )
        self.net = net
        self.menubar = self.createMenuBar()
        cframe = self.cframe = Frame( self )
        self.consoles = {}  # consoles themselves
        titles = {
            'hosts': 'Host',
        }
        for name in titles:
            nodes = getattr( net, name )
            nodes = [nodes[2],nodes[1],nodes[0],nodes[3]]
            frame, consoles = self.createConsoles(
                cframe, nodes, width, titles[ name ] )
            self.consoles[ name ] = Object( frame=frame, consoles=consoles )
        self.selected = None
        self.select( 'hosts' )
        self.cframe.pack( expand=True, fill='both' )
        cleanUpScreens()
        # Close window gracefully
        Wm.wm_protocol( self.top, name='WM_DELETE_WINDOW', func=self.quit )

        self.pack( expand=True, fill='both' )
#        self.textStyle = {
#            'font': 'Monaco 8',
#            'bg': 'white',
#            'fg': 'black',
#            'width': 60,
#            'height': 20,
#            'relief': 'sunken',
#            'insertbackground': 'green',
#            'highlightcolor': 'green',
#            'selectforeground': 'black',
#            'selectbackground': 'green'
#        }
#        self.textresult = Text(self.top,wrap='word', **self.textStyle)
#        self.textresult.pack(expand=True, fill='both')

    def setOutputHook( self, fn=None, consoles=None ):
        "Register fn as output hook [on specific consoles.]"
        if consoles is None:
            consoles = self.consoles[ 'hosts' ].consoles
        for console in consoles:
            console.outputHook = fn

    def createConsoles( self, parent, nodes, width, title ):
        "Create a grid of consoles in a frame."
        f = Frame( parent )
        # Create consoles
        consoles = []
        index = 0
        for node in nodes:
            console = Console( f, self.net, node, title=title )
            consoles.append( console )
            row = index / width
            column = index % width
            console.grid( row=row, column=column, sticky='nsew' )
            index += 1
            f.rowconfigure( row, weight=1 )
            f.columnconfigure( column, weight=1 )
        return f, consoles

    def select( self, groupName ):
        "Select a group of consoles to display."
        if self.selected is not None:
            self.selected.frame.pack_forget()
        self.selected = self.consoles[ groupName ]
        self.selected.frame.pack( expand=True, fill='both' )

    def createMenuBar( self ):
        "Create and return a menu (really button) bar."
        f = Frame( self, **self.frameStyle)
        buttons = [
            ( 'ping', self.ping ),
#            ( '验证', self.verify),
#            ( '清空', self.clear ),
            ( '退出', self.quit )
        ]
        for name, cmd in buttons:
            b = Button( f, text=name, command=cmd, **self.menuStyle )
            b.pack( side='left',fill='both',expand=True)
        f.pack( padx=4, pady=4, fill='both',expand=True)
        return f

    def clear( self ):
        "Clear selection."
        for console in self.selected.consoles:
            console.clear()

    def waiting( self, consoles=None ):
        "Are any of our hosts waiting for output?"
        if consoles is None:
            consoles = self.consoles[ 'hosts' ].consoles
        for console in consoles:
            if console.waiting():
                return True
        return False

    def ping( self ):
        "Tell each host to ping the next one."
        consoles = self.consoles[ 'hosts' ].consoles
        if self.waiting( consoles ):
            return
        for console in consoles:
            console.clear()
        consoles[0].sendCmd( 'ping -c 4 -W 3 10.0.0.1')
        consoles[1].sendCmd( 'ping -c 4 -W 3 10.0.0.3')
        consoles[2].sendCmd( 'ping -c 4 -W 3 10.0.0.4')
        consoles[3].sendCmd( 'ping -c 4 -W 3 10.0.0.2')
        
    def verify(self):
        consoles = self.consoles['hosts'].consoles
        for console in consoles:
            if console.route_boole:
                DumpFlowstoFiles(self.net.switches)#把交换机的流表输出到文件
                #开始根据流表分析路径
                path = console.node.name+'->'
                for i in range(1,len(switches)+1):
                    switch = 's'+str(i)
                    path+=CheckSwitchFlowBy98bytes(switch)
                path+=console.dstname
                f = open('result.txt','w')
                print '\n根据上面每个交换机的流表信息我们可以看出源主机到目的主机的路径为:\n'
                f.write('\n根据上面每个交换机的流表信息我们可以看出源主机到目的主机的路径为:\n')
                if(len(path)<=6):
                    print console.node.name+' 不能到达 '+console.dstname+'\n'
                    f.write(console.node.name+' 不能到达 '+console.dstname+'\n')
                else:
                    path = PathSrctoDst(path,network,console.node.name,console.dstname)
                    print '路线:'+path+'\n'
                    f.write('路线:'+path+'\n')
                f.close()
                f = open('result.txt','r')
                result = f.read()
                self.textresult.insert(INSERT,result)
            else:
                self.textresult.insert(INSERT,'当前没有流表记录无法验证')
                

    def stop( self, wait=True ):
        "Interrupt all hosts."
        consoles = self.consoles[ 'hosts' ].consoles
        for console in consoles:
            console.handleInt()
        if wait:
            for console in consoles:
                console.waitOutput()
        self.setOutputHook( None )
        # Shut down any iperfs that might still be running
        quietRun( 'killall -9 iperf' )

    def quit( self ):
        "Stop everything and quit."
        self.stop( wait=False)
        Frame.quit( self )


# Make it easier to construct and assign objects

def assign( obj, **kwargs ):
    "Set a bunch of fields in an object."
    obj.__dict__.update( kwargs )

class Object( object ):
    "Generic object you can stuff junk into."
    def __init__( self, **kwargs ):
        assign( self, **kwargs )


if __name__ == '__main__':
#    homedir = os.getcwd()
#    progress = StartFloodLight(' -cf '+homedir+'/no_fwd.properties')
    setLogLevel('info')
    mytopo = MyTopo()
    network = Mininet(topo = mytopo, controller = RemoteController,autoSetMacs=True)
    network.addController('SRCA')
    network.start()
    switches = network.switches
    hosts = network.hosts
    StaticFlows(switches,action='addarpflow')#给网络中的每个交换机的每个接口添加arp流表
    print '由于禁用了转发模块,所以在初始化网络的时候，交换机无法获取网络中每个节点的物理地址。'+\
        '为了让交换机获取网络当中每个节点的物理地址，我们给每个交换机的都添加了静态的arp流表。'+\
        '同时，为了实现静态流表的转发,我们在开启应用前让网络中的每个主机两两之间都进行ping。'
    network.ping(timeout='1' )
    app = ConsoleApp( network, width=4 )
    app.mainloop()
    StaticFlows(switches,action='delarpflow')#删除网络中的每个交换机的每个接口的特定流表
    network.stop()
#    StopFloodLight(progress)
