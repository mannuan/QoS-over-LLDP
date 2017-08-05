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
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import static net.floodlightcontroller.qosoverlldp.internal.QoSoverLLDPManager.QoSoverLLDPAPIList;

public class QoSoverLLDPAPIDisplayResource extends ServerResource{

    private static String indexhtml = new String();

    public String indexhtml1 =
        "<!DOCTYPE html>\n" +
        "<html>\n" +
        "<head>\n" +
        "<meta charset=\"UTF-8\">\n" +
        "<title>QOS over LLDP API</title>\n" +
        "<style>\n" +
        "body{\n" +
        "margin-top:0;\n" +
        "margin:0 auto;\n" +
        "background-color:#ccddff;\n" +
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
        "background-color:#ccddff;\n" +
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
        "background-color:#ccddff;\n" +
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
        "}input{\n" +
        "width:10%;\n" +
        "height:30px;\n" +
        "border-radius:10px;\n" +
        "font-size:20px  ;\n" +
        "font-family:Courier;\n" +
        "font-weight:bold;\n" +
        "background-color:#ccddff;"+
        "}.skip{\n" +
        "width:5%;\n" +
        "height:30px;\n" +
        "background-color:#ccddff;\n" +
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
        "color: #1d5494\n" +
        "}\n" +
        "</style>\n" +
        "</head>\n" +
        "<body>\n" +
        "<div id=title >QoS over LLDP API</div>\n" +
        "<header id=\"api\">http://<a id=\"ip_port\"></a>/wm/qosoverlldp/<a id=\"a_section\"><select id=\"section\" onchange=\"sectionSeclect()\">";
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
        "skip.onmouseover=function(){skip.style.backgroundColor=\"#0000FF\";}\n" +
        "skip.onmouseout=function(){skip.style.backgroundColor=\"#ccddff\";}\n" +
        "skip.onclick=function(){\t\n" +
        "var section=document.getElementById(\"section\").value;\n" +
        "var option1=document.getElementById(\"option1_select\").value;\n" +
        "var option2=document.getElementById(\"option2_input\").value;\n" +
        "var option3=document.getElementById(\"option3_input\").value;\n" +
        "var url=\"http://\"+ip_port+\"/wm/qosoverlldp/\"+section;\n" +
        "if(document.getElementById(\"a_option1\").style.display==\"\") url+=\"/\"+option1;\n" +
        "if(document.getElementById(\"a_option2\").style.display==\"\") url+=\"/\"+option2;\n" +
        "if(document.getElementById(\"a_option3\").style.display==\"\") url+=\"/\"+option3;\n" +
        "url+=\"/\"+document.getElementById(\"type\").innerHTML\n" +
        "window.open(url);\n" +
        "};\n" +
        "</script>\n";
    public String indexhtml4 ="\n"+
        "<div class=\"footer\">\n" +
        "<a class=\"footer_a\" href=\"http://www.swunix.com\" target=\"_blank\">关于QoS over LLDP</a>&nbsp;|&nbsp;\n" +
        "<a class=\"footer_a\" href=\"http://www.swunix.com\" target=\"_blank\">服务条款</a>&nbsp;|&nbsp;\n" +
        "<a class=\"footer_a\" href=\"http://www.swunix.com\" target=\"_blank\">联系我们</a>&nbsp;|&nbsp;\n" +
        "<a class=\"footer_a\" href=\"/ui/pages/qosoverlldp_historyqos.html\" target=\"_blank\">QoS over LLDP GUI</a>&nbsp;|&nbsp;\n" +
        "<a class=\"footer_a\" href=\"/ui/pages/qosoverlldp_statistics.html\" target=\"_blank\">QoS over LLDP Statistics</a>&nbsp;|&nbsp;\n" +
        "<a class=\"footer_a\" href=\"/ui/index.html\" target=\"_blank\">Floodlight GUI</a>&nbsp;|&nbsp;\n" +
        "<a class=\"footer_a\" href=\"/wm/qosoverlldp/floodlight-api.html\" target=\"_blank\">Floodlight API</a>&nbsp;|&nbsp;\n" +
        "<span class=\"gray\">Copyright (c) 2016 - 2017 SWUNIX Lab, swunix.com, Inc. All Rights Reserved.</span></div>\n" +
        "</body>\n</html>";


    @Get("html")
    public String retrieve(){
        if(!indexhtml.isEmpty()){
            return indexhtml;
        }
        String resources = new String();
        String resource_script =
        "<script type=\"text/javascript\">\n";
        if(!QoSoverLLDPAPIList.isEmpty()){
            resource_script +=
            "document.getElementById(\"type\").innerHTML=\""+QoSoverLLDPAPIList.get(0).getType()+"\";"+
                    "document.getElementById(\"section\").style.width="+QoSoverLLDPAPIList.get(0).getResource().length()*12.5+"+\"px\"\n"+
                    "document.getElementById(\"option2_input\").style.width="+QoSoverLLDPAPIList.get(0).getParameter2().length()*12.5+"+\"px\"\n"+
                    "document.getElementById(\"option3_input\").style.width="+QoSoverLLDPAPIList.get(0).getParameter3().length()*12.5+"+\"px\"\n"+
                    "document.getElementById(\"option2_input\").setAttribute(\"placeholder\",\"" + QoSoverLLDPAPIList.get(0).getParameter2() + "\");\n"+
                    "document.getElementById(\"option3_input\").setAttribute(\"placeholder\",\"" + QoSoverLLDPAPIList.get(0).getParameter3() + "\");\n";
            for(String option1:QoSoverLLDPAPIList.get(0).getParameter1_option()){
                resource_script+="var option1_select=document.getElementById('option1_select');\n"+
                        "option1_select.options.add(new Option(\""+option1+"\",\""+option1+"\"));\n";
            }
            resource_script +=
                    "document.getElementById(\"description\").innerHTML=\"API用法：</br>"+QoSoverLLDPAPIList.get(0).getDescription()+"\";";
            if(QoSoverLLDPAPIList.get(0).getParameter1().isEmpty()){
                resource_script+="document.getElementById(\"a_option1\").style.display=\"none\";\n";
            }else{
                resource_script+="document.getElementById(\"a_option1\").style.display=\"\";\n";
            }
            if(QoSoverLLDPAPIList.get(0).getParameter2().isEmpty()){
                resource_script+="document.getElementById(\"a_option2\").style.display=\"none\";\n";
            }else{
                resource_script+="document.getElementById(\"a_option2\").style.display=\"\";\n";
            }
            if(QoSoverLLDPAPIList.get(0).getParameter3().isEmpty()){
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
        for(QoSoverLLDPAPI api:QoSoverLLDPAPIList){
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
        indexhtml = indexhtml1+resources+indexhtml2+indexhtml3+resource_script+option1_script+indexhtml4;
        return indexhtml;
    }

}
