# include <stdio.h>
# include <stdlib.h>
int main ()
{
	long int num = 8192;
	char* szBuffer = (char *)malloc(sizeof(long int) + 1);  //分配动态内存
	memset(szBuffer, 0, sizeof(long int) + 1);              //内存块初始化
	sprintf(szBuffer, "%ld", num);                  //整数转化为字符串
	printf("The number 'num' is %ld and the string 'str' is %s. \n" ,num, szBuffer);
	free(szBuffer);                                    //释放动态分配的内存
    return 0;
}
