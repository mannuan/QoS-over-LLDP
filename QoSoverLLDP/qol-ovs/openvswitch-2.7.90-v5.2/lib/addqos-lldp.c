/**
 * Copyright (c) 2016 - 2017 WangLing Lab, Inc.
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

#include "addqos-lldp.h"

VLOG_DEFINE_THIS_MODULE(addqos_lldp);

/**
 *把lldp数据包里面的数据转成16进制的字符串
 */
char*
bytes_to_string(const void* buf_, size_t maxbytes)
{
    const uint8_t *buf = buf_;
    struct ds s;
    ds_init(&s);
    for(int i = 0; i < maxbytes; i++) ds_put_format(&s, "%02x", buf[i]);
    return ds_cstr(&s);
}

/**
 * 字符串拼接函数
 * len:表示字符串的长度,也就是malloc出来的长度
 * 使用方法如:
 * char *ptr;
 * 几个字符串就几个%s
 * ptr = xm_vsprintf_ex(20, "%s%s%s%s", "/tmp/s", "1", "-eth","2");
 * free(ptr);
 */
char*
xm_vsprintf_ex(int len, char *fmt, ... )
{
    va_list ap;
    char *ptr;
    ptr = (char *)malloc(len * sizeof(char));
    if(ptr == NULL)
    {
//        fprintf(stderr, "malloc failed\n");
        return NULL;
    }
    memset(ptr, 0, len);
    va_start(ap, fmt);
    vsprintf(ptr, fmt, ap);
    va_end(ap);
    ptr[len-1] = '\0';
    return ptr;
}

/**
 * 注意:把数据读入buf时会把'\0'读到末尾的，所以不要考虑'\0'
 */
int
send_cmd(char* cmd)
{
	FILE   *stream;
    if((stream = popen(cmd, "r")) == NULL){
    	//VLOG_INFO(ERROR_CANTCREATE_FILENO);
    	return 0;
    }
    memset(buf,'\0',BUFSIZ);
    int read_len = fread( buf, sizeof(char), sizeof(buf), stream);/**注意这里的len计算了'\0'的长度*/
    pclose(stream);
    return read_len;
}

/**
 *参数:hstr:16进制的字符串地址;len:16进制字符串的长度
 */
char*
hexstr_to_longstr(char* hstr, int len)
{
	long sum = 0;
	int t = 0;
	for(int i=0;i<len;i++){
		if(hstr[i] <= '9') t=hstr[i]-'0';
		else t = hstr[i]-'a'+10;
		sum=sum*16+t;
	}
    char* longstr = (char*)malloc(sizeof(char)*11);
    sprintf(longstr,"%ld",sum);
	return longstr;
}

/**
 * 字符串翻转
 * @param p 起始翻转位置
 * @param size 要翻转的长度
 */
void
Reverse(unsigned char* p,int size){
    int i;
    unsigned char tmp;
    for(i = 0; i < size/2; i++){
    	tmp = p[i];
    	p[i] = p[size-1-i];
    	p[size-1-i] = tmp;
    }
}

/**
 * 读取qos信息，并把它填入报文
 * @param file 读取qos信息的文件的名称
 * @param dst 目的地
 * @param dst_size 可以填写数据的目的地最大字节
 * @param type 填入目的地的数据的类型，目前只有两种:double、long
 */
void
read_set(char* file,unsigned char* dst,int dst_size,char type){
	if(file != NULL){
		if(access(file,F_OK)==0){//如果文件存在
			char value[100];
			FILE *fptr;
			if((fptr = fopen(file,"r")) == NULL) return;
			int len = fread(value,sizeof(char),sizeof(value),fptr);
			value[len-1]='\0';
			fclose(fptr);
			unsigned char* ucvalue = (unsigned char*)malloc(dst_size);
			char* endptr = NULL;
			if(type == 'f'){
				double d = strtod(value,&endptr);
				memcpy(ucvalue,&d,sizeof(d)<=dst_size?sizeof(d):dst_size);
			}else if(type == 'l'){
				long l = strtol(value,&endptr,10);
				memcpy(ucvalue,&l,sizeof(l)<=dst_size?sizeof(l):dst_size);
			}
			Reverse(ucvalue,dst_size);
			memcpy(dst,ucvalue,dst_size);
			free(ucvalue);
			free(file);
		}
	}
}

/**
 * add_qos_to_lldp 是 qos over lldp 在ovswitch上的主程序
 */
void
add_qos_to_lldp(struct ofpbuf* packet){
	/**
	 * 第一步:
	 *         先把packet_in的数据包的16进制数据转换成字符串
	 *         如: 04122356......
	 */
    char* packetdatastr = bytes_to_string(packet->data,packet->size);
    /**
     * 第二步:
     *         判断是不是lldp数据包,如果是,就获取chassisid的值
     */
    char* pchassisid = strstr(packetdatastr,LINK_LAYER_DISCOVERY_PROTOCOL_CHASSISID_FLAG);
    if(pchassisid == NULL){/**如果不是lldp数据包提前退出函数*/
//    	VLOG_INFO(ERROR_ISNOTLLDP);
//    	VLOG_INFO(ERROR_NOTFOUND_CHASSISID);
	    return;
    }
//    VLOG_INFO("packetdatastr:%s",packetdatastr);/**是lldp数据包才把数据输出到日志文件*/
    pchassisid += strlen(LINK_LAYER_DISCOVERY_PROTOCOL_CHASSISID_FLAG);
    char* chassisid = hexstr_to_longstr(pchassisid,12);//注意:这里的chassissid是一个10进制的long型数(以字符串形式存在),等同与switchid
//    VLOG_INFO("chassisid:%s",chassisid);/**如果找到则把chassisid输出到日志文件*/
    /**
     * 第三步:
     * 		   获取设备的portid
     */
    char* pportid = strstr(pchassisid,LINK_LAYER_DISCOVERY_PROTOCOL_PORTID_FLAG);
    if(pportid == NULL){
//    	VLOG_INFO(ERROR_NOTFOUND_PORTID);
    	return;
    }
    pportid += strlen(LINK_LAYER_DISCOVERY_PROTOCOL_PORTID_FLAG);
    char* portid = hexstr_to_longstr(pportid,4);
//    VLOG_INFO("portid:%s",portid);
    /**
     *第四步
     *		判断是否使用tc,没有则直接退出
     */
    char* tc_class_cmd = xm_vsprintf_ex(100,"tc class show dev s%s-eth%s",chassisid,portid);
    int len = send_cmd(tc_class_cmd);
    free(tc_class_cmd);
    if(len<1) return;
    /**
     *第五步:
     *        判断是否包含qostlv，并且获取qostlv在packet->data里面的位置
     *返回qostlv的标志位的信息
     */
    int tlvtype_tlvlength = (TLV_TYPE << 9) | (strlen(TLV_SUBTYPE)/2 + BANDWIDTH_SIZE + DELAY_SIZE + JITTER_SIZE + LOSS_SIZE);//tlv_type7位tlv_length9位
    unsigned char ptlvtype_tlvlength[] = {(unsigned char)(tlvtype_tlvlength>>8),(unsigned char)tlvtype_tlvlength};
    char* hexstr_type_len = bytes_to_string(ptlvtype_tlvlength,2);//16进制的type_len字符串
    char* qostlv_flag = xm_vsprintf_ex(100,"%s%s",hexstr_type_len,TLV_SUBTYPE);
//    VLOG_INFO("qostlv_flag:%s",qostlv_flag);
    char* pqos = NULL;
    if(qostlv_flag != NULL){
		pqos = strstr(pportid,qostlv_flag);//在指向portid的这个指针的字符串里面寻找存放qos信息的位置
		if(pqos == NULL){/**如果不包含qostlv提前退出函数*/
	//	    VLOG_INFO(ERROR_WITHOUT_QOSTLV);
			return;
		}
		pqos += strlen(qostlv_flag);
//		VLOG_INFO("strlen(qostlv_flag):%d",strlen(qostlv_flag));
    }
    /**
     * 第六步：
     * 			生成记录qos信息的临时文件
     * 流程：
     * 1.判断是否存在存放临时文件的目录
     * 1.判断是否有qosoverlldp.sh这个脚本，否则就dump一个名为qosoverlldp.sh的文件到tmp目录
     * 先判断是否产生了进程，没有则执行qosoverlldp.sh脚本
     * 在执行linux shell 脚本的过程中,不断的计算qos信息,然后有不断的把它们输出到文件中,如果遇到错误那么就把生成的所有文件删除（包括qosoverlldp.sh）
     * 最后又重复上面的流程,知道虚拟交换机停止工作为止
     */
	if(access(QOSOVERLLDP_FOLDER_NAME,F_OK)!=0){//如果(存放qosoverlldp项目所有tmp文件的)目录不存在则创建
		mkdir(QOSOVERLLDP_FOLDER_NAME, S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH);
	}
	int chassisid_len = strlen(chassisid);
	int portid_len = strlen(portid);
	int folder_len = strlen(QOSOVERLLDP_FOLDER_NAME);
	int shname_len = strlen(QOSOVERLLDP_SH_NAME);
	char* qosoverlldp_shfile = xm_vsprintf_ex(folder_len+2+shname_len,"%s%s",QOSOVERLLDP_FOLDER_NAME,QOSOVERLLDP_SH_NAME);
    if(access(QOSOVERLLDP_FOLDER_NAME,F_OK)==0 && access(qosoverlldp_shfile,F_OK)!=0){//如果目录存在，并且qosoverlldp.sh文件不存在,创建
    	FILE *fptr;
    	if((fptr = fopen(qosoverlldp_shfile,"w")) == NULL) return;
    	char* qosoverlldp_sh_content = QOSOVERLLDP_SH_CONTENT;
    	fwrite(qosoverlldp_sh_content,sizeof(char),strlen(qosoverlldp_sh_content),fptr);
    	fclose(fptr);
    }
    //注意字符串的长度，是不是超了
    char* process_format = "ps ax | grep 'sh %s%s s%s-eth%s' | sed '/grep/d' | awk '{print $1}'";
	char* process_cmd = xm_vsprintf_ex(strlen(process_format)+folder_len+strlen(QOSOVERLLDP_SH_NAME)+chassisid_len+portid_len,process_format,
			QOSOVERLLDP_FOLDER_NAME,QOSOVERLLDP_SH_NAME,chassisid,portid);//获取进程号的字符串，如果查无此进程则返回一个字符'\n',如果有进程号则字符串的长度必大于1
	if(access(qosoverlldp_shfile,F_OK)==0 && send_cmd(process_cmd) <= 1){//如果有qosoverlldp.sh这个文件,并且没有在运行这个linux shell脚本
		//这里linu shell 脚本命令行参数的顺序依次是：端口的名称、采集qos的时间间隔、存放带宽信息的文件的关键字、存放时延信息的文件的关键字、存放抖动信息的文件的关键字、
		//存放丢包率信息的文件的关键字、linux shell 脚本的文件名、存放qos信息的绝对路径
		char* sh_cmd = xm_vsprintf_ex(strlen("sh %s%s s%s-eth%s %s %s %s %s %s %s %s &")+folder_len+shname_len+chassisid_len+portid_len+
				strlen(INTERVAL)+strlen(BANDWIFTH_FILE)+strlen(DELAY_FILE)+strlen(JITTER_FILE)+strlen(LOSS_FILE)+shname_len+folder_len,
				"sh %s%s s%s-eth%s %s %s %s %s %s %s %s &",QOSOVERLLDP_FOLDER_NAME,QOSOVERLLDP_SH_NAME,chassisid,portid,
				INTERVAL,BANDWIFTH_FILE,DELAY_FILE,JITTER_FILE,LOSS_FILE,QOSOVERLLDP_SH_NAME,QOSOVERLLDP_FOLDER_NAME);//注意字符串的长度，是不是超了
		if(system(sh_cmd) == -1){
			free(sh_cmd);
			free(process_cmd);
			free(qosoverlldp_shfile);
			return;
		}
		free(sh_cmd);
	}
	free(process_cmd);
	free(qosoverlldp_shfile);
    /**
     * 第七步
    * qostlv在packet->data的位置，由于packetdatastr里面一个字符是一个16进制数,
     * 而在packet里面一个字符是一个字节，所以要除以2
     * */
    int qos_loc = (pqos-packetdatastr)/2;
//    VLOG_INFO("qos_loc:%d",qos_loc);
    free(packetdatastr);/**由于获取了chassisid和portid后就释放packetdatastr了*/
    /**
     * 注意，下面的这几步都是读取实时脚本生成的数据
     * 这时候如果不进行错误判断:最好的情况下，就是无法填写qos的其他的值，更糟糕的还有:
     * 直接导致虚拟交换机的程序出现崩溃，因此在进行下面几个步骤时，考虑异常情况尤为重要；
     * 我之前在设置qos值的时候，由于memset设置溢出,导致时间戳改变，然后影响了floodlight其他模块的运行
     * 所以，在进行任何操作的时候都要考虑清楚模块之间的相互影响
     */
    unsigned char* data = (unsigned char*)packet->data;/**packet->data的单位是一个字节*/
	/** 第八步:
	 *         获取剩余带宽的值并设置它
	 */
	unsigned char* pbw = data + qos_loc;/**获取指向带宽的指针*/
	int dynamic_len = strlen("%s/s%s-eth%s%s");
	char* remainbwfile = xm_vsprintf_ex(dynamic_len+folder_len+chassisid_len+portid_len+strlen(BANDWIFTH_FILE),
			"%s/s%s-eth%s%s",QOSOVERLLDP_FOLDER_NAME,chassisid,portid,BANDWIFTH_FILE);//存放剩余带宽文件的路径
	read_set(remainbwfile,pbw,BANDWIDTH_SIZE,'l');
	/** 第九步:
	 *         获取时延的值并设置它
	 */
	unsigned char* pdelay = data + qos_loc + BANDWIDTH_SIZE;/**BANDWIDTH_SIZE表示带宽占的字节数,获取指向时延的指针*/
	char* delayfile = xm_vsprintf_ex(dynamic_len+folder_len+chassisid_len+portid_len+strlen(DELAY_FILE),
			"%s/s%s-eth%s%s",QOSOVERLLDP_FOLDER_NAME,chassisid,portid,DELAY_FILE);//存放时延的文件的路径
	read_set(delayfile,pdelay,DELAY_SIZE,'l');
	/** 第十步:
	 *         获取抖动的值并设置它
	 */
	unsigned char* pjitter = data + qos_loc + BANDWIDTH_SIZE+DELAY_SIZE;/**BANDWIDTH_SIZE+DELAY_SIZE表示带宽和时延占的字节数,获取指向抖动的指针*/
	char* jitterfile = xm_vsprintf_ex(dynamic_len+folder_len+chassisid_len+portid_len+strlen(JITTER_FILE),
			"%s/s%s-eth%s%s",QOSOVERLLDP_FOLDER_NAME,chassisid,portid,JITTER_FILE);//存放抖动文件的路径
	read_set(jitterfile,pjitter,JITTER_SIZE,'l');
	/** 第十一步:
	 *         获取丢包率的值并设置它
	 */
	unsigned char* ploss = data + qos_loc + BANDWIDTH_SIZE+DELAY_SIZE+JITTER_SIZE;/**表示带宽和时延和抖动占的字节数,获取指向丢包率的指针*/
	char* lossfile = xm_vsprintf_ex(dynamic_len+folder_len+chassisid_len+portid_len+strlen(LOSS_FILE),
			"%s/s%s-eth%s%s",QOSOVERLLDP_FOLDER_NAME,chassisid,portid,LOSS_FILE);//存放丢包率文件的路径
	read_set(lossfile,ploss,LOSS_SIZE,'f');
	//最后一步
    free(chassisid);
    free(portid);
}
