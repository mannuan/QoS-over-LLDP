/**
 * Copyright (c) 2016 - 2017 SWUNIX Lab, swunix.com, Inc.
 *
 *  You may obtain a copy of the License at:
 *
 *       http://www.swunix.com
 *
 *       vlog默认的输出文件是/var/log/openvswitch/ovs-vswitchd.log
 *       在floodlight上面设置qostlv的时候要注意了,不要把qostlv的位置放在timestamp的后面，否则就会影响floodlight的功能
 *       tc查看命令：带宽 tc class show dev s1-eth1, 延迟、抖动、丢包率 tc qdisc show dev s1-eth1
 *       使用tc命令把qos设置为0，是无法设置的，所以在判断0的时候无需考虑太多
 *       带宽和丢包率这两个qos值是独立的，不受其他qos信息的影响,但是延迟和抖动是相互影响的，唯有设置了延迟才可以设置抖动，
 *       因此，在openvswitch设置jitter的时候应该先判断有无delay,否则不需要判断
 *       注意：linux 的tc 支持带宽设置的范围为:8bit~1Gbit;(而且只能8bit的设置)单位有:bit,Kbit,Mbit,Gbit
 *                   delay延迟的范围(0,274877906]us,mininet设置的单位默认是us,也就是延迟最大是247.9s，然后tc设置时间的时候只保留到小数点后一位
 *                   单位有:us,ms,s
 *                   抖动(jitter)的范围(0,274877906]        单位:us 注意:jitter是依赖于delay的,也就是说没有delay就没有jitter,有delay但是可以没有jitter
 *                   丢包率(delay)的范围[0,100]
 *       在floodlight中加入qostlv时千万不可以改变chassisid和portidtlv的位置,其他随便,否则qos over lldp项目无法实现,但是不会影响交换机的正常工作
 *       在mininet中设置0.000008Mbit(8bit)的时候，在终端可能显示0.00Mbit,但是确实设置进去了,如下:
 *       class htb 5:1 root leaf 10: prio 0 rate 8bit ceil 8bit burst 1b cburst 225b
 *       把实现qostlv的功能加在了net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager.generateLLDPMessage方法里面
 *       只需要一行代码:lldp.getOptionalTLVList().add(qosTLV);(放这行代码要注意:不要把qostlv的位置放在timestamp的后面)
 *       另外由于要改lldp发送的时间间隔,所以要改net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager中的LLDP_TO_ALL_INTERVAL参数,
 *       把protected final int LLDP_TO_ALL_INTERVAL = 15;把protected final修饰符改为public static修饰符
 *       还有要/wm/qosoverlldp/proactive/json立刻生效就要修改lldpClock的属性把protected final 改为public static
 *       把protected final long lldpClock = 0;把protected final修饰符改为public static修饰符
 *       把对LLDP进行QoS信息的填写的功能加在packet_out解码的函数里面，具体位置是ofp-util.c的文件的ofputil_decode_packet_out函数里面，大概在文件的第4190行
 *		 填写的位置如下所示：
 *
 *		 struct ofpbuf b = ofpbuf_const_initializer(oh, ntohs(oh->length));
 *
 *		 add_qos_to_lldp(&b);
 *
 *		 也就是放在ofpbuf下面，因为我们的主程序要调用它
 *
 *		 在进行floodlight编程的时候，一定要注意：千万不要乱导包，导入的外部包类名如果与原来的包冲突，就会影响整个floodlight的正常运行
 *		 为了使QoSoverLLDP模块生效，要在如下两个配置文件里面添加net.floodlightcontroller.qosoverlldp.internal.QoSoverLLDPManager
 *		 qol-fl/floodlight-v5.0/src/main/resources/META-INF/services/net.floodlightcontroller.core.module.IFloodlightModule
 *		 qol-fl/floodlight-v5.0/src/main/resources/floodlightdefault.properties
 *		 以上两个文件的添加方式是不同的，第一个配置只需要加在文件末尾即可
 *		 但是第二个配置文件，必须考虑清楚，应该如下：
 *		 net.floodlightcontroller.statistics.StatisticsCollector,\
 *       net.floodlightcontroller.qosoverlldp.internal.QoSoverLLDPManager
 *       也就是说上面要加的那句话应该放在net.floodlightcontroller这类模块的最后，并与上一个模块用逗号隔开
 */

package net.floodlightcontroller.qosoverlldp.web;

import net.floodlightcontroller.qosoverlldp.internal.QoSoverLLDPAPI;
import net.floodlightcontroller.qosoverlldp.internal.QoSoverLLDPManager;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class FloodlightAPIDisplayResource extends ServerResource {

    public ArrayList<QoSoverLLDPAPI> FloodlightAPIList= new ArrayList<>();

    private static String floodlight_apihtml = new String();

    private void GenerateFloodlightAPIList() {
        QoSoverLLDPAPI api_1 = new QoSoverLLDPAPI("acl/rules","json");
        api_1.setDescription(1,"ACL模块的api，用来返回当前所有规则。");
        FloodlightAPIList.add(api_1);

        QoSoverLLDPAPI api_2 = new QoSoverLLDPAPI("acl/clear","json");
        api_2.setDescription(2,"ACL模块的api，用来情空所有规则。");
        FloodlightAPIList.add(api_2);

        QoSoverLLDPAPI api_3 = new QoSoverLLDPAPI("core/module/all","json");
        api_3.setDescription(3,"控制器核心模块的api，用来显示控制器上的所有的模块。");
        FloodlightAPIList.add(api_3);

        QoSoverLLDPAPI api_4 = new QoSoverLLDPAPI("core/module/loaded","json");
        api_4.setDescription(4,"控制器核心模块的api，用来显示控制器上的所有已经加载的模块。");
        FloodlightAPIList.add(api_4);

        QoSoverLLDPAPI api_5 = new QoSoverLLDPAPI("core/swicth","role/json");
        api_5.setDescription(5,"控制器核心模块的api，输入交换机的id来显示交换机的角色信息，输入格式：'s'加一个整数。这里还有一个api：/wm/core/swicth/{swicthId}/{statType}/json，无法在列表显示");
        api_5.setParameter("{switchId}");
        FloodlightAPIList.add(api_5);

        QoSoverLLDPAPI api_6 = new QoSoverLLDPAPI("core/swicth/all","json");
        api_6.setDescription(6,"控制器核心模块的api，输入格式未知。");
        api_6.setParameter("{statType}");
        FloodlightAPIList.add(api_6);

        QoSoverLLDPAPI api_7 = new QoSoverLLDPAPI("core/controller/switches","json");
        api_7.setDescription(7,"控制器核心模块的api，显示控制器上面的所有交换机。");
        FloodlightAPIList.add(api_7);

        QoSoverLLDPAPI api_8 = new QoSoverLLDPAPI("core/counter","json");
        api_8.setDescription(8,"控制器核心模块的api，功能未知");
        api_8.setParameter("{counterModule}","{counterTitle}");
        FloodlightAPIList.add(api_8);

        QoSoverLLDPAPI api_9 = new QoSoverLLDPAPI("core/memory","json");
        api_9.setDescription(9,"控制器核心模块的api,.....");
        FloodlightAPIList.add(api_9);

        QoSoverLLDPAPI api_10 = new QoSoverLLDPAPI("core/packettrace","json");
        api_10.setDescription(10,"控制器核心模块的api,.....");
        FloodlightAPIList.add(api_10);

        QoSoverLLDPAPI api_11 = new QoSoverLLDPAPI("core/storage/tables","json");
        api_11.setDescription(11,"控制器核心模块的api,.....");
        FloodlightAPIList.add(api_11);

        QoSoverLLDPAPI api_12 = new QoSoverLLDPAPI("core/controller/summary","json");
        api_12.setDescription(12,"控制器核心模块的api,.....");
        FloodlightAPIList.add(api_12);

        QoSoverLLDPAPI api_13 = new QoSoverLLDPAPI("core/role","json");
        api_13.setDescription(13,"控制器核心模块的api,.....");
        FloodlightAPIList.add(api_13);

        QoSoverLLDPAPI api_14 = new QoSoverLLDPAPI("core/health","json");
        api_14.setDescription(14,"控制器核心模块的api，显示控制器的健康状况。");
        FloodlightAPIList.add(api_14);

        QoSoverLLDPAPI api_15 = new QoSoverLLDPAPI("core/system/uptime","json");
        api_15.setDescription(15,"控制器核心模块的api，显示控制器的运行持续时间。");
        FloodlightAPIList.add(api_15);

        QoSoverLLDPAPI api_16 = new QoSoverLLDPAPI("core/version","json");
        api_16.setDescription(16,"控制器核心模块的api，显示控制器的版本。");
        FloodlightAPIList.add(api_16);

        QoSoverLLDPAPI api_17 = new QoSoverLLDPAPI("device/all","json");
        api_17.setDescription(17,"控制器设备模块的api，用来显示所有主机的信息。");
        FloodlightAPIList.add(api_17);

        QoSoverLLDPAPI api_18 = new QoSoverLLDPAPI("device","");
        api_18.setDescription(18,"控制器设备模块的api，用来显示所有主机的信息。另外一个api：/wm/device/debug不方便显示。");
        FloodlightAPIList.add(api_18);

        QoSoverLLDPAPI api_19 = new QoSoverLLDPAPI("dhcpserver/add/instance","json");
        api_19.setDescription(19,"控制器dhcp模块的api，。。。。");
        FloodlightAPIList.add(api_19);

        QoSoverLLDPAPI api_20 = new QoSoverLLDPAPI("dhcpserver/get/instance","json");
        api_20.setDescription(20,"控制器dhcp模块的api，....");
        api_20.setParameter("{instance}");
        FloodlightAPIList.add(api_20);

        QoSoverLLDPAPI api_21 = new QoSoverLLDPAPI("dhcpserver/del/instance","json");
        api_21.setDescription(21,"控制器dhcp模块的api，....");
        api_21.setParameter("{instance}");
        FloodlightAPIList.add(api_21);

        QoSoverLLDPAPI api_22 = new QoSoverLLDPAPI("dhcpserver/add/static-binding","json");
        api_22.setDescription(22,"控制器dhcp模块的api，。。。。");
        FloodlightAPIList.add(api_22);

        QoSoverLLDPAPI api_23 = new QoSoverLLDPAPI("firewall/module/status","json");
        api_23.setDescription(23,"控制器防火墙模块的api，....");
        FloodlightAPIList.add(api_23);

        QoSoverLLDPAPI api_24 = new QoSoverLLDPAPI("firewall/module/enable","json");
        api_24.setDescription(24,"控制器防火墙模块的api，....");
        FloodlightAPIList.add(api_24);

        QoSoverLLDPAPI api_25 = new QoSoverLLDPAPI("firewall/module/disable","json");
        api_25.setDescription(25,"控制器防火墙模块的api，....");
        FloodlightAPIList.add(api_25);

        QoSoverLLDPAPI api_26 = new QoSoverLLDPAPI("firewall/module/subnet-mask","json");
        api_26.setDescription(26,"控制器防火墙模块的api，....");
        FloodlightAPIList.add(api_26);

        QoSoverLLDPAPI api_27 = new QoSoverLLDPAPI("firewall/module/subnet-mask","json");
        api_27.setDescription(27,"控制器防火墙模块的api，....");
        FloodlightAPIList.add(api_27);

        QoSoverLLDPAPI api_28 = new QoSoverLLDPAPI("firewall/module/storageRules","json");
        api_28.setDescription(28,"控制器防火墙模块的api，....");
        FloodlightAPIList.add(api_28);

        QoSoverLLDPAPI api_29 = new QoSoverLLDPAPI("firewall/rules","json");
        api_29.setDescription(29,"控制器防火墙模块的api，....");
        FloodlightAPIList.add(api_29);

        QoSoverLLDPAPI api_30 = new QoSoverLLDPAPI("learningswitch/table","json");
        api_30.setDescription(30,"控制器交换机学习模块的api，.....");
        api_30.setParameter("{switch}");
        FloodlightAPIList.add(api_30);

        QoSoverLLDPAPI api_31 = new QoSoverLLDPAPI("linkdiscovery/autoportfast","json");
        api_31.setDescription(31,"控制器链路发现模块的api，....");
        api_31.setParameter("{state}");
        FloodlightAPIList.add(api_31);

        QoSoverLLDPAPI api_32 = new QoSoverLLDPAPI("performance/data","json");
        api_32.setDescription(32,"控制器性能监视器模块的api，.....");
        FloodlightAPIList.add(api_32);

        QoSoverLLDPAPI api_33 = new QoSoverLLDPAPI("performance","json");
        api_33.setDescription(33,"控制器性能监视器模块的api，.....");
        api_33.setParameter("{perfmonstate}");
        FloodlightAPIList.add(api_33);

        QoSoverLLDPAPI api_34 = new QoSoverLLDPAPI("routing/metric","json");
        api_34.setDescription(34,"控制器路由模块的api，............。其它api无法在页面显示：/routing//path/{src-dpid}/{src-port}/{dst-dpid}/{dst-port}/json，" +
                "/routing/paths/{src-dpid}/{dst-dpid}/{num-paths}/json，/routing/paths/fast/{src-dpid}/{dst-dpid}/{num-paths}/json，/routing/paths/slow/{src-dpid}/{dst-dpid}/{num-paths}/json");
        FloodlightAPIList.add(api_34);

        QoSoverLLDPAPI api_35 = new QoSoverLLDPAPI("routing/paths/force-recompute/","json");
        api_35.setDescription(35,"控制器路由模块的api，............");
        FloodlightAPIList.add(api_35);

        QoSoverLLDPAPI api_36 = new QoSoverLLDPAPI("routing/paths/max-fast-paths","json");
        api_36.setDescription(36,"控制器路由模块的api，............");
        FloodlightAPIList.add(api_36);

        QoSoverLLDPAPI api_37 = new QoSoverLLDPAPI("staticflowpusher","json");
        api_37.setDescription(37,"控制器静态流表推送器模块的api，.......");
        FloodlightAPIList.add(api_37);

        QoSoverLLDPAPI api_38 = new QoSoverLLDPAPI("staticflowpusher/clear","json");
        api_38.setDescription(38,"控制器静态流表推送器模块的api，.......");
        api_38.setParameter("{switch}");
        FloodlightAPIList.add(api_38);

        QoSoverLLDPAPI api_39 = new QoSoverLLDPAPI("staticflowpusher/list","json");
        api_39.setDescription(39,"控制器静态流表推送器模块的api，.......");
        api_39.setParameter("{switch}");
        FloodlightAPIList.add(api_39);

        QoSoverLLDPAPI api_40 = new QoSoverLLDPAPI("staticflowpusher/usage","json");
        api_40.setDescription(40,"控制器静态流表推送器模块的api，.......");
        FloodlightAPIList.add(api_40);

        QoSoverLLDPAPI api_41 = new QoSoverLLDPAPI("statistics/bandwidth","json");
        api_41.setDescription(41,"控制器统计模块的api，.....");
        api_41.setParameter("dpid","port");
        FloodlightAPIList.add(api_41);

        QoSoverLLDPAPI api_42 = new QoSoverLLDPAPI("statistics/config/enable","json");
        api_42.setDescription(42,"控制器统计模块的api，.....");
        FloodlightAPIList.add(api_42);

        QoSoverLLDPAPI api_43 = new QoSoverLLDPAPI("statistics/config/disable","json");
        api_43.setDescription(42,"控制器统计模块的api，.....");
        FloodlightAPIList.add(api_43);

        QoSoverLLDPAPI api_44 = new QoSoverLLDPAPI("storage/notify","json");
        api_44.setDescription(44,"控制器存储模块的api，....");
        FloodlightAPIList.add(api_44);

        QoSoverLLDPAPI api_45 = new QoSoverLLDPAPI("topology/links","json");
        api_45.setDescription(45,"控制器拓扑模块的api，返回所有链路信息。");
        FloodlightAPIList.add(api_45);

        QoSoverLLDPAPI api_46 = new QoSoverLLDPAPI("topology/directed-links","json");
        api_46.setDescription(46,"控制器拓扑模块的api，返回与控制器直连的链路信息。");
        FloodlightAPIList.add(api_46);

        QoSoverLLDPAPI api_47 = new QoSoverLLDPAPI("topology/external-links","json");
        api_47.setDescription(47,"控制器拓扑模块的api，返回外部控制器的链路信息。");
        FloodlightAPIList.add(api_47);

        QoSoverLLDPAPI api_48 = new QoSoverLLDPAPI("topology/tunnellinks","json");
        api_48.setDescription(48,"控制器拓扑模块的api，返回控制器的隧道链路信息。");
        FloodlightAPIList.add(api_48);

        QoSoverLLDPAPI api_49 = new QoSoverLLDPAPI("topology/archipelagos","json");
        api_49.setDescription(49,"控制器拓扑模块的api，返回控制器的域信息。");
        FloodlightAPIList.add(api_49);

        QoSoverLLDPAPI api_50 = new QoSoverLLDPAPI("topology/broadcastports","json");
        api_50.setDescription(50,"控制器拓扑模块的api，返回控制器的广播端口信息。");
        FloodlightAPIList.add(api_50);

        QoSoverLLDPAPI api_51 = new QoSoverLLDPAPI("topology/enabledports","json");
        api_51.setDescription(51,"控制器拓扑模块的api，返回控制器的开启端口信息。");
        FloodlightAPIList.add(api_51);

        QoSoverLLDPAPI api_52 = new QoSoverLLDPAPI("topology/blockedports","json");
        api_52.setDescription(52,"控制器拓扑模块的api，返回控制器的受阻端口信息。");
        FloodlightAPIList.add(api_52);

    }

    public String indexhtml1 =
            "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "<meta charset=\"UTF-8\">\n" +
                    "<title>Floodlight API</title>\n" +
                    "<style>\n" +
                    "body{\n" +
                    "margin-top:0;\n" +
                    "margin:0 auto;\n" +
                    "background-color:#FFFF99;\n" +
                    "}#title{\n" +
                    "margin-top:50px;\n" +
                    "text-align:center;\n" +
                    "font-size:50px  ;\n" +
                    "font-family:Times New Roman;\n" +
                    "}#api{\n" +
                    "text-align:center;\n" +
                    "font-size:20px  ;\n" +
                    "font-family:Courier;\n" +
                    "font-weight:bold;\n" +
                    "margin-top:100px;\n" +
                    "width:100%;\n" +
                    "height:100%;\n" +
                    "}#a_section{\n" +
                    "width:300px;\n" +
                    "height:40px;\n" +
                    "}#section{\n" +
                    "width:156px;\n" +
                    "height:40px;\n" +
                    "overflow:hidden;\n" +
                    "border-radius:10px;\n" +
                    "background-color:#FFFF99;\n" +
                    "appearance:none;\n" +
                    "text-align:center;\n" +
                    "-moz-appearance:none;\n" +
                    "-webkit-appearance:none;\n" +
                    "font-size:20px;\n" +
                    "font-family:Courier;\n" +
                    "font-weight:bold;\n" +
                    "background-attachment:fixed;\n" +
                    "border:2px solid #ccddff;\n" +
                    "border:none;\n" +
                    "}#option1_select{\n" +
                    "width:115px;\n" +
                    "height:40px;\n" +
                    "overflow:hidden;\n" +
                    "border-radius:10px;\n" +
                    "background-color:#FFFF99;\n" +
                    "appearance:none;\n" +
                    "text-align:center;\n" +
                    "-moz-appearance:none;\n" +
                    "-webkit-appearance:none;\n" +
                    "font-size:20px;\n" +
                    "font-family:Courier;\n" +
                    "font-weight:bold;\n" +
                    "background-attachment:fixed;\n" +
                    "border:2px solid #FFFF99;\n" +
                    "border:none;\n" +
                    "}input{\n" +
                    "width:10%;\n" +
                    "height:30px;\n" +
                    "border-radius:10px;\n" +
                    "font-size:20px  ;\n" +
                    "font-family:Courier;\n" +
                    "font-weight:bold;\n" +
                    "background-color:#FFFF99;"+
                    "}.skip{\n" +
                    "width:5%;\n" +
                    "height:30px;\n" +
                    "background-color:#FFFF99;\n" +
                    "}.feature{\n" +
                    "margin-top:40px;\n" +
                    "font-size:20px;\n" +
                    "font-family:宋体;\n" +
                    "text-align:center;\n" +
                    "}.footer {\n" +
                    "position: fixed;\n" +
                    "_position: static;\n" +
                    "left: 0;\n" +
                    "right: 0;\n" +
                    "bottom: 0;\n" +
                    "z-index: 2;\n" +
                    "clear: both;\n" +
                    "line-height: 36px;\n" +
                    "text-align: center;\n" +
                    "color: #b6b6b6;\n" +
                    "background-color: #eff4fa;\n" +
                    "border-top: 1px solid #d6dfea;\n" +
                    "font-family: \"lucida Grande\", Verdana, \"Microsoft YaHei\";\n" +
                    "font-size: 12px;\n" +
                    "color: #868686;\n" +
                    "}.footer_a{\n" +
                    "color: #FF9900\n" +
                    "}\n" +
                    "</style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "<div id=title >Floodlight API</div>\n" +
                    "<header id=\"api\">http://<a id=\"ip_port\"></a>/wm/<a id=\"a_section\"><select id=\"section\" onchange=\"sectionSeclect()\">";
    public String indexhtml2 =
            "</select></a><a id=\"a_option1\">/<select id=\"option1_select\" onchange=\"option1Select()\">\n";
    public String indexhtml3 =
            "</select></a><a id=\"a_option2\">/<input id=\"option2_input\"placeholder=\"option2\"></a><a id=\"a_option3\">/<input id=\"option3_input\" placeholder=\"option3\"></a>/<a id=\"type\">type</a>\n" +
                    "<button id=\"skip\" class=\"skip\">GO</button>\n" +
                    "</header>\n" +
                    "<p id=\"description\" class=\"feature\"></p>"+
                    "<script type=\"text/javascript\">\n" +
                    "var ip_port=window.location.hostname+\":\"+window.location.port;\n" +
                    "document.getElementById(\"ip_port\").innerHTML=ip_port;\n" +
                    "var skip=document.getElementById(\"skip\")\n" +
                    "skip.onmouseover=function(){skip.style.backgroundColor=\"#FF9900\";}\n" +
                    "skip.onmouseout=function(){skip.style.backgroundColor=\"#FFFF99\";}\n" +
                    "skip.onclick=function(){\t\n" +
                    "var section=document.getElementById(\"section\").value;\n" +
                    "var option1=document.getElementById(\"option1_select\").value;\n" +
                    "var option2=document.getElementById(\"option2_input\").value;\n" +
                    "var option3=document.getElementById(\"option3_input\").value;\n" +
                    "var url=\"http://\"+ip_port+\"/wm/\"+section;\n" +
                    "if(document.getElementById(\"a_option1\").style.display==\"\") url+=\"/\"+option1;\n" +
                    "if(document.getElementById(\"a_option2\").style.display==\"\") url+=\"/\"+option2;\n" +
                    "if(document.getElementById(\"a_option3\").style.display==\"\") url+=\"/\"+option3;\n" +
                    "url+=\"/\"+document.getElementById(\"type\").innerHTML\n" +
                    "window.open(url);\n" +
                    "};\n" +
                    "</script>\n";
    public String indexhtml4 ="\n"+
            "<div class=\"footer\">\n" +
            "<a class=\"footer_a\" href=\"http://www.projectfloodlight.org/floodlight/\" target=\"_blank\">关于Floodlight</a>&nbsp;|&nbsp;\n" +
            "<a class=\"footer_a\" href=\"/wm/qosoverlldp/api/index.html\" target=\"_blank\">QoS over LLDP API</a>&nbsp;|&nbsp;\n" +
            "<a class=\"footer_a\" href=\"/ui/pages/qosoverlldp_historyqos.html\" target=\"_blank\">QoS over LLDP GUI</a>&nbsp;|&nbsp;\n" +
            "<a class=\"footer_a\" href=\"/ui/pages/qosoverlldp_statistics.html\" target=\"_blank\">QoS over LLDP Statistics</a>&nbsp;|&nbsp;\n" +
            "<a class=\"footer_a\" href=\"/ui/index.html\" target=\"_blank\">Floodlight GUI</a>&nbsp;|&nbsp;\n" +
            "<span class=\"gray\">Copyright (c) 2016 - 2017 SWUNIX Lab, swunix.com, Inc. All Rights Reserved.</span></div>\n" +
            "</body>\n</html>";


    @Get("html")
    public String retrieve(){
        if(!floodlight_apihtml.isEmpty()){
            return floodlight_apihtml;
        }
        GenerateFloodlightAPIList();
        String resources = new String();
        String resource_script =
                "<script type=\"text/javascript\">\n";
        if(!FloodlightAPIList.isEmpty()){
            resource_script +=
                    "document.getElementById(\"type\").innerHTML=\""+FloodlightAPIList.get(0).getType()+"\";"+
                            "document.getElementById(\"section\").style.width="+FloodlightAPIList.get(0).getResource().length()*12.5+"+\"px\"\n"+
                            "document.getElementById(\"option2_input\").style.width="+FloodlightAPIList.get(0).getParameter2().length()*12.5+"+\"px\"\n"+
                            "document.getElementById(\"option3_input\").style.width="+FloodlightAPIList.get(0).getParameter3().length()*12.5+"+\"px\"\n"+
                            "document.getElementById(\"option2_input\").setAttribute(\"placeholder\",\"" + FloodlightAPIList.get(0).getParameter2() + "\");\n"+
                            "document.getElementById(\"option3_input\").setAttribute(\"placeholder\",\"" + FloodlightAPIList.get(0).getParameter3() + "\");\n";
            for(String option1:FloodlightAPIList.get(0).getParameter1_option()){
                resource_script+="var option1_select=document.getElementById('option1_select');\n"+
                        "option1_select.options.add(new Option(\""+option1+"\",\""+option1+"\"));\n";
            }
            resource_script +=
                    "document.getElementById(\"description\").innerHTML=\"API用法：</br>"+FloodlightAPIList.get(0).getDescription()+"\";";
            if(FloodlightAPIList.get(0).getParameter1().isEmpty()){
                resource_script+="document.getElementById(\"a_option1\").style.display=\"none\";\n";
            }else{
                resource_script+="document.getElementById(\"a_option1\").style.display=\"\";\n";
            }
            if(FloodlightAPIList.get(0).getParameter2().isEmpty()){
                resource_script+="document.getElementById(\"a_option2\").style.display=\"none\";\n";
            }else{
                resource_script+="document.getElementById(\"a_option2\").style.display=\"\";\n";
            }
            if(FloodlightAPIList.get(0).getParameter3().isEmpty()){
                resource_script+="document.getElementById(\"a_option3\").style.display=\"none\";\n";
            }else{
                resource_script+="document.getElementById(\"a_option3\").style.display=\"\";\n";
            }
        }
        resource_script+=
                "function sectionSeclect(){\n" +
                        "document.getElementById(\"option2_input\").value=\"\";\n"+
                        "document.getElementById(\"option3_input\").value=\"\";\n"+
                        "switch(document.getElementById(\"section\").value){\n";
        Set<String> option1_set = new HashSet<String>();
        for(QoSoverLLDPAPI api:FloodlightAPIList){
            resources+="<option value=\""+api.getResource()+"\">"+api.getResource()+"</option>\n";
            option1_set.addAll(api.getParameter1_option());
            resource_script+=
                    "case \""+api.getResource()+"\":\n" +
                            "document.getElementById(\"type\").innerHTML=\""+api.getType()+"\";"+
                            "document.getElementById(\"section\").style.width="+api.getResource().length()*12.5+"+\"px\";\n" +
                            "document.getElementById(\"option2_input\").style.width="+api.getParameter2().length()*12.5+"+\"px\"\n"+
                            "document.getElementById(\"option3_input\").style.width="+api.getParameter3().length()*12.5+"+\"px\"\n"+
                            "document.getElementById(\"option2_input\").setAttribute(\"placeholder\",\""+api.getParameter2()+"\");\n"+
                            "document.getElementById(\"option3_input\").setAttribute(\"placeholder\",\""+api.getParameter3()+"\");\n"+
                            "document.getElementById(\"description\").innerHTML=\"API用法：</br>"+api.getDescription()+"\";\n";
            if(api.getParameter1().isEmpty()){
                resource_script+="document.getElementById(\"a_option1\").style.display=\"none\";\n";
            }else{
                resource_script+="document.getElementById(\"a_option1\").style.display=\"\";\n";
            }
            if(api.getParameter2().isEmpty()){
                resource_script+="document.getElementById(\"a_option2\").style.display=\"none\";\n";
            }else{
                resource_script+="document.getElementById(\"a_option2\").style.display=\"\";\n";
            }
            if(api.getParameter3().isEmpty()){
                resource_script+="document.getElementById(\"a_option3\").style.display=\"none\";\n";
            }else{
                resource_script+="document.getElementById(\"a_option3\").style.display=\"\";\n";
            }
            resource_script+=
                    "var option1_select=document.getElementById('option1_select');\n"+
                            "option1_select.options.length=0;\n";
            for(String option1:api.getParameter1_option()){
                resource_script+=
                        "option1_select.options.add(new Option(\""+option1+"\",\""+option1+"\"));\n";
            }
            if(!api.getParameter1_option().isEmpty()){
                resource_script+="option1_select.style.width="+api.getParameter1_option().get(0).length()*12.5+"+\"px\"\n";
            }
            resource_script+=
                    "break;\n";
        }
        resource_script+="}}</script>";
        ArrayList<String> option1_list = new ArrayList<>();
        option1_list.addAll(option1_set);
        String option1_script = "<script type=\"text/javascript\">\n";
        if(!option1_list.isEmpty()){
            option1_script+="document.getElementById(\"option1_select\").style.width="+option1_list.get(0).length()*12.5+"+\"px\"\n";
        }
        option1_script+="function option1Select(){\n" +
                "switch(document.getElementById(\"option1_select\").value){\n";
        for(String option1:option1_list){
            option1_script+=
                    "case \""+option1+"\":\n" +
                            "document.getElementById(\"option1_select\").style.width="+option1.length()*12.5+"+\"px\";\n" +
                            "break;\n";
        }
        option1_script+="}}</script>";
        floodlight_apihtml = indexhtml1+resources+indexhtml2+indexhtml3+resource_script+option1_script+indexhtml4;
        return floodlight_apihtml;
    }

}
