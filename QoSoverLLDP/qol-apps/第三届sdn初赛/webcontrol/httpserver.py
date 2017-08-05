# -*- coding: utf-8 -*-
"""
Created on Wed May 25 14:12:35 2016

@author: wjl
"""

import BaseHTTPServer
from SimpleHTTPServer import SimpleHTTPRequestHandler
HandlerClass = SimpleHTTPRequestHandler
ServerClass  = BaseHTTPServer.HTTPServer
Protocol     = "HTTP/1.0"
f = open('WebIP.txt')
if(f < 0):
    ip = '10.0.0.1'
else:
    ip = f.read()
f.close()
server_address = (ip, 80)
HandlerClass.protocol_version = Protocol
httpd = ServerClass(server_address, HandlerClass)
sa = httpd.socket.getsockname()
print "Serving HTTP on", sa[0], "port", sa[1], "..."
httpd.serve_forever()