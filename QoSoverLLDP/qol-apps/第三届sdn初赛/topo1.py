__author__ = 'wjl'
# encoding utf-8
from mininet.topo import Topo

class MyTopo(Topo):
	def __init__(self):

		Topo. __init__(self)

		#add Hosts and Switchs
		s1 = self.addSwitch('s1')
		s2 = self.addSwitch('s2')
		H1 = self.addHost('H1')
		H2 = self.addHost('H2')
		H3 = self.addHost('H3')
		H4 = self.addHost('H4')
		#add Links
		self.addLink(H1,s1)
		self.addLink(H2,s1)
		self.addLink(s1,s2)
		self.addLink(H3,s2)
		self.addLink(H4,s2)

topos = { 'mytopo' : ( lambda : MyTopo() ) }


		
