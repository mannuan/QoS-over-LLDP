#bash stop_ovs_vsctl.sh
sudo /usr/share/openvswitch/scripts/ovs-ctl stop
sudo apt-get autoremove openvswitch-common openvswitch-switch 
#openvswitch-pki openvswitch-testcontroller
#openvswitch-datapath-dkms openvswitch-datapath-source 
sudo apt-get clean
sudo apt-get autoclean

