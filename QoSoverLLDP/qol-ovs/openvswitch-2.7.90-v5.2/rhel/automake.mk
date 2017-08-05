# Copyright (C) 2009, 2010, 2011, 2012, 2014 Nicira, Inc.
#
# Copying and distribution of this file, with or without modification,
# are permitted in any medium without royalty provided the copyright
# notice and this notice are preserved.  This file is offered as-is,
# without warranty of any kind.

EXTRA_DIST += \
	rhel/README.RHEL.rst \
	rhel/automake.mk \
	rhel/etc_init.d_openvswitch \
	rhel/etc_logrotate.d_openvswitch \
	rhel/etc_sysconfig_network-scripts_ifdown-ovs \
	rhel/etc_sysconfig_network-scripts_ifup-ovs \
	rhel/openvswitch-dkms.spec \
	rhel/openvswitch-dkms.spec.in \
	rhel/openvswitch-kmod-rhel6.spec \
	rhel/openvswitch-kmod-rhel6.spec.in \
	rhel/openvswitch-kmod.files \
	rhel/openvswitch-kmod-fedora.spec \
	rhel/openvswitch-kmod-fedora.spec.in \
	rhel/openvswitch.spec \
	rhel/openvswitch.spec.in \
	rhel/openvswitch-fedora.spec \
	rhel/openvswitch-fedora.spec.in \
	rhel/usr_share_openvswitch_scripts_sysconfig.template \
	rhel/usr_share_openvswitch_scripts_systemd_sysconfig.template \
	rhel/usr_lib_systemd_system_openvswitch.service \
	rhel/usr_lib_systemd_system_ovsdb-server.service \
	rhel/usr_lib_systemd_system_ovs-vswitchd.service \
	rhel/usr_lib_systemd_system_ovn-controller.service \
	rhel/usr_lib_systemd_system_ovn-controller-vtep.service \
	rhel/usr_lib_systemd_system_ovn-northd.service \
	rhel/usr_lib_firewalld_services_ovn-central-firewall-service.xml \
	rhel/usr_lib_firewalld_services_ovn-host-firewall-service.xml

update_rhel_spec = \
  $(AM_V_GEN)($(ro_shell) && sed -e 's,[@]VERSION[@],$(VERSION),g') \
    < $(srcdir)/rhel/$(@F).in > $(@F).tmp || exit 1; \
  if cmp -s $(@F).tmp $@; then touch $@; rm $(@F).tmp; else mv $(@F).tmp $@; fi

$(srcdir)/rhel/openvswitch-dkms.spec: rhel/openvswitch-dkms.spec.in $(top_builddir)/config.status
	$(update_rhel_spec)

$(srcdir)/rhel/openvswitch-kmod-rhel6.spec: rhel/openvswitch-kmod-rhel6.spec.in $(top_builddir)/config.status
	$(update_rhel_spec)

$(srcdir)/rhel/openvswitch-kmod-fedora.spec: rhel/openvswitch-kmod-fedora.spec.in $(top_builddir)/config.status
	$(update_rhel_spec)

$(srcdir)/rhel/openvswitch.spec: rhel/openvswitch.spec.in $(top_builddir)/config.status
	$(update_rhel_spec)

$(srcdir)/rhel/openvswitch-fedora.spec: rhel/openvswitch-fedora.spec.in $(top_builddir)/config.status
	$(update_rhel_spec)

RPMBUILD_TOP := $(abs_top_builddir)/rpm/rpmbuild

# Build user-space RPMs
rpm-fedora: dist $(srcdir)/rhel/openvswitch-fedora.spec
	${MKDIR_P} ${RPMBUILD_TOP}/SOURCES
	cp ${DIST_ARCHIVES} ${RPMBUILD_TOP}/SOURCES
	rpmbuild ${RPMBUILD_OPT} \
                 -D "_topdir ${RPMBUILD_TOP}" \
                 -ba $(srcdir)/rhel/openvswitch-fedora.spec

# Build kernel datapath RPM
rpm-fedora-kmod: dist $(srcdir)/rhel/openvswitch-kmod-fedora.spec
	${MKDIR_P} ${RPMBUILD_TOP}/SOURCES
	cp ${DIST_ARCHIVES} ${RPMBUILD_TOP}/SOURCES
	rpmbuild -D "kversion $(shell uname -r)" ${RPMBUILD_OPT} \
                 -D "_topdir ${RPMBUILD_TOP}" \
                 -ba $(srcdir)/rhel/openvswitch-kmod-fedora.spec
