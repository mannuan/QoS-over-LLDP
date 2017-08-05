#include <stdlib.h>
#include <stdio.h>
#include <string.h>
char buf[512];
char* uciget(char option[])
{
FILE   *stream; 
memset( buf, '\0', sizeof(buf) );
    stream = popen( option, "r" ); 
    fread( buf, sizeof(char), sizeof(buf), stream);
buf[strlen(buf)-1]= '\0';
    pclose(stream);

return buf;
}
int main(void) 
{ 
	char *bandwidth_return = (char*)malloc(sizeof(char)*1000);
	int i;
	for(i=0;uciget("tc class show dev s1-eth1")[i]!='\0';i++){
	}
	memcpy(bandwidth_return,uciget("tc class show dev s1-eth1"),i);
	bandwidth_return[i]='\0';
	printf("%s",bandwidth_return);
	return 1;
 }
