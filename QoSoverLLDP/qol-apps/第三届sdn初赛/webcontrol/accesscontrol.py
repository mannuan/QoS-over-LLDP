# -*- coding: utf-8 -*-
"""
Created on Thu Apr 21 16:15:53 2016
这是web控制的主程序
@author: wjl
"""
import re,os

from Tkinter import Frame, Button,Text, Scrollbar, Wm, READABLE,StringVar

from mininet.log import setLogLevel
from mininet.net import Mininet
from mininet.node import RemoteController
from mininet.term import cleanUpScreens
from mininet.util import quietRun
from third_topo import MyTopo
from myutil import StartFloodLight,StopFloodLight,ACL

class Console( Frame ):
    "A simple console on a host."

    def __init__( self, parent, net, node, height=16, width=18, title='Node' ):
        Frame.__init__( self, parent )

        self.net = net
        self.node = node
        self.prompt = node.name + '# '
        self.height, self.width, self.title = height, width, title
        
        self.hostname = StringVar()
        self.hostname.set('把'+self.node.name+'添加到黑名单')
        self.host_boole = False
        self.ruleid = 1;
        
        self.webstatus = StringVar()
        self.webstatus.set('开启web服务器')
        self.web_boole = False
        
        self.firefoxstatus = StringVar()
        self.firefoxstatus.set('使用firefox访问web主页')
        self.firefox_boole = False

        # Initialize widget styles
        self.buttonStyle = { 
            'font': 'Monaco 10', 
            'bg': 'green',
            'fg': 'white'
        }
        self.buttonStyle1 = { 
            'font': 'Monaco 10', 
        }
        self.textStyle = {
            'font': 'Monaco 10',
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

        def hostACL():
            if(self.host_boole):
                pusher = ACL('127.0.0.1')
                acl = {
                    "ruleid":self.ruleid
                }
                pusher.remove(acl)
                print '我删除了'+self.node.name+'的acl'
                self.host_boole = False
                self.hostname.set('把'+self.node.name+'添加到黑名单')
                label['bg'] = 'green'
            else:
                pusher = ACL('127.0.0.1')
                acl = {
                    "src-ip":self.node.IP()+'/32',
                    "dst-ip":"10.0.0.5/32",
                    "nw-proto":"TCP",
                    "tp-dst":80,
                    "action":"deny"
                }
                pusher.set(acl)
                print '我添加了'+self.node.name+'的acl'
                self.host_boole = True
                self.hostname.set('从黑名单删除'+self.node.name)
                label['bg'] = 'red' 
                f = open('count.txt')
                count = f.read()
                icount = int(count)
                f.close()
                self.ruleid = icount;
                os.system('echo '+str(icount + 1)+' > count.txt')
                
        def WebServer():
            if(self.web_boole):
                self.sendCmd('kill %python')
                self.clear()
                self.webstatus.set('开启web服务器')
                self.web_boole = False
            else:
                self.sendCmd('python httpserver.py &')
                self.webstatus.set('关闭web服务器')
                self.web_boole = True
            
        if(self.node.name.find('Web') == 0):
            label = Button( self, textvariable=self.webstatus, command=WebServer,
                           **self.buttonStyle1 )
        else:
            label = Button( self, textvariable=self.hostname, 
                           command=hostACL, **self.buttonStyle )
        label.pack( side='top', fill='x' )
        if(self.node.name.find('Web') != 0):
            def firefox():
                if(self.firefox_boole):
                    self.sendCmd('kill %firefox')
                    self.clear()
                    self.firefoxstatus.set('使用firefox访问web主页')
                    self.firefox_boole = False
                else:
                    self.sendCmd( 'firefox '+self.net.get('Web').IP()+' &')
                    self.firefoxstatus.set('关闭并清除firefox缓存')
                    self.firefox_boole = True
                    
            firefox = Button( self, textvariable=self.firefoxstatus, 
                                command=firefox,**self.buttonStyle1 )
            firefox.pack( side='top', fill='x' )
        
        text = Text( self, wrap='word', **self.textStyle )
        ybar = Scrollbar( self, orient='vertical', width=7,
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
        'font': 'Geneva 10 bold',
        'width': 16
        }

    def __init__( self, net, parent=None, width=4 ):
        Frame.__init__( self, parent )
        #parameter
        self.net = net
        self.selected = None
        self.consoles = {}  # consoles themselves
        cframe = self.cframe = Frame( self )
        Hnodes = [net.hosts[0],net.hosts[1],net.hosts[2],net.hosts[3]]
        Webnodes = [net.hosts[4]]
        frame, consoles = self.createConsoles(
            cframe, Hnodes, width, 'Host' )
        self.consoles[ 'hosts' ] = Object( frame=frame, consoles=consoles )
        frame, consoles = self.createConsoles(
            cframe, Webnodes, width, 'Web' )
        self.consoles[ 'web' ] = Object( frame=frame, consoles=consoles )
        
        #parent
        self.top = self.winfo_toplevel()
        self.top.title( 'Mininet' )
        
        #menu
        self.menubar = self.createMenuBar()
        
        #consoles
        self.select( 'hosts' )
        self.cframe.pack( expand=True, fill='both' )
        cleanUpScreens()
        
        # Close window gracefully
        Wm.wm_protocol( self.top, name='WM_DELETE_WINDOW', func=self.quit )
        #palce and display
        self.pack( expand=True, fill='both' )

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
        f = Frame( self )
        buttons = [
            ( '主机', lambda: self.select( 'hosts' ) ),
            ( 'Web服务器', lambda: self.select( 'web' ) ),
            ( 'wget', self.wget),
            ( '中断', self.stop ),
            ( '清空', self.clear ),
            ( '退出', self.quit )
        ]
        for name, cmd in buttons:
            b = Button( f, text=name, command=cmd, **self.menuStyle )
            b.pack( side='left' )
        f.pack( padx=4, pady=4, fill='x' )
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
        
    def wget( self ):
        "Tell each host to ping the next one."
        consoles = self.consoles[ 'hosts' ].consoles
        if self.waiting( consoles ):
            return
        for console in consoles:
            console.sendCmd( 'wget -O - '+self.net.get('Web').IP())
            
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
        "stop web server."
        consoles = self.consoles[ 'web' ].consoles
        for console in consoles:
            console.sendCmd( 'kill %python')
            console.handleInt()
        self.setOutputHook( None )
        # Shut down any iperfs that might still be running
        quietRun( 'killall -9 iperf' )
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
#    progress = StartFloodLight()
    os.system('echo 1 > count.txt')
    setLogLevel( 'info' )
    topo = MyTopo()
    network = Mininet(topo=topo,controller=RemoteController,autoSetMacs=True)
    network.addController('consoles')
    network.start()
    os.system('echo '+network.get('Web').IP()+' > WebIP.txt')
    app = ConsoleApp( network, width=4 )
    app.mainloop()
    network.stop()
#    StopFloodLight(progress)
