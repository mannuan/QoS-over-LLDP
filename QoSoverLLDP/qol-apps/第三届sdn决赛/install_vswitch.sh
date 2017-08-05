sudo /usr/share/openvswitch/scripts/ovs-ctl stop
sudo dpkg -i openvswitch-common_2.5.0-1_amd64.deb openvswitch-switch_2.5.0-1_amd64.deb 
#openvswitch-pki_2.5.0-1_all.deb openvswitch-testcontroller_2.5.0-1_amd64.deb
#sudo apt-get install libntdb1 python-ntdb
sudo /usr/share/openvswitch/scripts/ovs-ctl start
#openvswitch-datapath-dkms_2.5.0-1_all.deb openvswitch-datapath-source_2.5.0-1_all.deb
#sudo dpkg -i openvswitch-common_2.4.0-1_amd64.deb openvswitch-switch_2.4.0-1_amd64.deb
#cd openvswitch-2.5.0/
#./configure --with-linux=/lib/modules/`uname -r`/build
#make && make install
#insmod datapath/linux/openvswitch.ko

#sudo dpkg -i openvswitch-common_2.5.0-1_amd64.deb openvswitch-switch_2.5.0-1_amd64.deb openvswitch-datapath-dkms_2.5.0-1_all.deb openvswitch-datapath-source_2.5.0-1_all.deb openvswitch-pki_2.5.0-1_all.deb openvswitch-testcontroller_2.5.0-1_amd64.deb openvswitch-vtep_2.5.0-1_amd64.deb openvswitch-dbg_2.5.0-1_amd64.deb openvswitch-test_2.5.0-1_all.deb python-openvswitch_2.5.0-1_all.deb openvswitch-ipsec_2.5.0-1_amd64.deb
