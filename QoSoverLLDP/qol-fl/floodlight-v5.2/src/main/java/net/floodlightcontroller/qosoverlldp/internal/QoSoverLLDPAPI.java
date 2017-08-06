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

package net.floodlightcontroller.qosoverlldp.internal;

import java.util.ArrayList;

public class QoSoverLLDPAPI {

    protected Class _class;//实现API的类的名称
    protected String resource;//请求资源的名称
    protected String segment = "/";//数据格式前面分隔符
    protected String type;//呈现的数据格式
    protected String description;//API的功能
    protected String parameter1;//参数1
    protected ArrayList<String> parameter1_option;//参数1的固定选项
    protected String parameter2;//参数2
    protected String parameter3;//参数3
    protected String parameter4;//参数4

    /**
     * API的初始化
     * @param _class java类的类名
     * @param resource 请求资源的名称
     * @param type 返回的数据类型
     */
    public QoSoverLLDPAPI(Class _class, String resource, String type){
        this._class = _class;
        this.resource = resource;
        this.type = type;
        this.description = new String();
        this.parameter1 = new String();
        this.parameter1_option = new ArrayList<>();
        this.parameter2 = new String();
        this.parameter3 = new String();
        this.parameter4 = new String();
    }

    /**
     * API的初始化
     * @param resource 请求资源的名称
     * @param type 返回的数据类型
     */
    public QoSoverLLDPAPI(String resource, String type){
        this._class = null;
        this.resource = resource;
        this.type = type;
        this.description = new String();
        this.parameter1 = new String();
        this.parameter1_option = new ArrayList<>();
        this.parameter2 = new String();
        this.parameter3 = new String();
        this.parameter4 = new String();
    }

    /**
     * API的初始化
     * @param _class java类的类名
     * @param resource 请求资源的名称
     * @param segment 数据格式前面分隔符
     * @param type 返回的数据类型
     */
    public QoSoverLLDPAPI(Class _class, String resource, String segment, String type){
        this._class = _class;
        this.resource = resource;
        this.segment = segment;
        this.type = type;
        this.description = new String();
        this.parameter1 = new String();
        this.parameter1_option = new ArrayList<>();
        this.parameter2 = new String();
        this.parameter3 = new String();
        this.parameter4 = new String();
    }

    /**
     * API的初始化
     * @param resource 请求资源的名称
     * @param segment 数据格式前面分隔符
     * @param type 返回的数据类型
     */
    public QoSoverLLDPAPI(String resource, String segment, String type){
        this._class = null;
        this.resource = resource;
        this.segment = segment;
        this.type = type;
        this.description = new String();
        this.parameter1 = new String();
        this.parameter1_option = new ArrayList<>();
        this.parameter2 = new String();
        this.parameter3 = new String();
        this.parameter4 = new String();
    }

    /**
     * 设置API功能的文字描述
     * @param description
     */
    public void setDescription(String description){
        this.description = description;
    }

    /**
     *
     * @param description
     * @param order 序号
     */
    public void setDescription(int order,String description){
        this.description = String.format("API-%d：%s",order,description);
    }

    /**
     *
     * @param a
     * @param a_opt
     * @param b
     * @param c
     */
    public void setParameter(String a, ArrayList<String> a_opt,String b,String c){
        if(!a.contains("{")) a="{"+a;
        if(!a.contains("}")) a+="}";
        this.parameter1 = a;
        this.parameter1_option = a_opt;
        if(!b.contains("{")) b="{"+b;
        if(!b.contains("}")) b+="}";
        this.parameter2 = b;
        if(!c.contains("{")) c="{"+c;
        if(!c.contains("}")) c+="}";
        this.parameter3 = c;
    }

    /**
     *
     * @param a
     * @param a_opt
     * @param b
     */
    public void setParameter(String a, ArrayList<String> a_opt,String b){
        if(!a.contains("{")) a="{"+a;
        if(!a.contains("}")) a+="}";
        this.parameter1 = a;
        this.parameter1_option = a_opt;
        if(!b.contains("{")) b="{"+b;
        if(!b.contains("}")) b+="}";
        this.parameter2 = b;
    }


    /**
     *
     * @param a
     * @param a_opt
     */
    public void setParameter(String a, ArrayList<String> a_opt){
        if(!a.contains("{")) a="{"+a;
        if(!a.contains("}")) a+="}";
        this.parameter1 = a;
        this.parameter1_option = a_opt;
    }

    /**
     *
     * @param a
     * @param b
     * @param c
     */
    public void setParameter(String a,String b,String c){
        if(!a.contains("{")) a="{"+a;
        if(!a.contains("}")) a+="}";
        this.parameter2 = a;
        if(!b.contains("{")) b="{"+b;
        if(!b.contains("}")) b+="}";
        this.parameter3 = b;
        if(!c.contains("{")) c="{"+c;
        if(!c.contains("}")) c+="c";
        this.parameter4 = c;
    }

    /**
     *
     * @param a
     * @param b
     */
    public void setParameter(String a,String b){
        if(!a.contains("{")) a="{"+a;
        if(!a.contains("}")) a+="}";
        this.parameter2 = a;
        if(!b.contains("{")) b="{"+b;
        if(!b.contains("}")) b+="}";
        this.parameter3 = b;
    }

    /**
     *
     * @param a
     */
    public void setParameter(String a){
        if(!a.contains("{")) a="{"+a;
        if(!a.contains("}")) a+="}";
        this.parameter2 = a;
    }

    /**
     * 返回java的类名
     * @return
     */
    public Class get_Class(){
        return this._class;
    }

    /**
     * 返回请求资源的名称
     * @return
     */
    public String getResource(){
        return this.resource;
    }

    /**
     * 返回数据格式前面的分隔符
     * @return
     */
    public String getSegment(){
        return this.segment;
    }

    /**
     * 返回数据类型
     * @return
     */
    public String getType(){
        return this.type;
    }

    public String getDescription(){
        return this.description;
    }

    public ArrayList<String> getParameter1_option(){
        return this.parameter1_option;
    }

    public String getParameter1(){
        return this.parameter1;
    }

    public String getParameter2(){
        return this.parameter2;
    }
    public String getParameter3(){
        return this.parameter3;
    }
    public String getParameter4(){
        return this.parameter4;
    }

    /**
     * 返回url的后面半截
     * @return path
     */
    public String getPath(){
        String path="/"+this.resource;
        if(!this.parameter1.isEmpty()){
            path+="/"+this.parameter1;
        }
        if(!this.parameter2.isEmpty()){
            path+="/"+this.parameter2;
        }
        if(!this.parameter3.isEmpty()){
            path+="/"+this.parameter3;
        }
        if(!this.parameter4.isEmpty()){
            path+="/"+this.parameter4;
        }
        path+=this.segment+this.type;
        return path;
    }

}