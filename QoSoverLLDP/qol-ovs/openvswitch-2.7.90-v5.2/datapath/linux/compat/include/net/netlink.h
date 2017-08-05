#ifndef __NET_NETLINK_WRAPPER_H
#define __NET_NETLINK_WRAPPER_H 1

#include <linux/version.h>
#include_next <net/netlink.h>
#include_next <linux/in6.h>

#ifndef HAVE_NLA_GET_BE16
/**
 * nla_get_be16 - return payload of __be16 attribute
 * @nla: __be16 netlink attribute
 */
static inline __be16 nla_get_be16(const struct nlattr *nla)
{
	return *(__be16 *) nla_data(nla);
}
#endif  /* !HAVE_NLA_GET_BE16 */

#if LINUX_VERSION_CODE < KERNEL_VERSION(2,6,34)
/* This function was introduced in 2.6.31, but initially it performed an
 * unaligned access, so we replace it up to 2.6.34 where it was fixed.  */
#define nla_get_be64 rpl_nla_get_be64
static inline __be64 nla_get_be64(const struct nlattr *nla)
{
	__be64 tmp;

	/* The additional cast is necessary because  */
	nla_memcpy(&tmp, (struct nlattr *) nla, sizeof(tmp));

	return tmp;
}
#endif

#ifndef HAVE_NLA_PUT_BE16
static inline int nla_put_be16(struct sk_buff *skb, int attrtype, __be16 value)
{
	return nla_put(skb, attrtype, sizeof(__be16), &value);
}
#endif

#ifndef HAVE_NLA_PUT_BE32
static inline int nla_put_be32(struct sk_buff *skb, int attrtype, __be32 value)
{
	return nla_put(skb, attrtype, sizeof(__be32), &value);
}
#endif

#ifndef HAVE_NLA_PUT_BE64
static inline int nla_put_be64(struct sk_buff *skb, int attrtype, __be64 value)
{
	return nla_put(skb, attrtype, sizeof(__be64), &value);
}
#endif

#ifndef nla_for_each_nested
#define nla_for_each_nested(pos, nla, rem) \
	nla_for_each_attr(pos, nla_data(nla), nla_len(nla), rem)
#endif

#ifndef HAVE_NLA_FIND_NESTED
static inline struct nlattr *nla_find_nested(struct nlattr *nla, int attrtype)
{
	return nla_find(nla_data(nla), nla_len(nla), attrtype);
}
#endif

#ifndef HAVE_NLA_IS_LAST
static inline bool nla_is_last(const struct nlattr *nla, int rem)
{
	return nla->nla_len == rem;
}
#endif

#ifndef HAVE_NLA_PUT_IN_ADDR
static inline int nla_put_in_addr(struct sk_buff *skb, int attrtype,
				  __be32 addr)
{
	return nla_put_be32(skb, attrtype, addr);
}

static inline int nla_put_in6_addr(struct sk_buff *skb, int attrtype,
				   const struct in6_addr *addr)
{
	return nla_put(skb, attrtype, sizeof(*addr), addr);
}

static inline __be32 nla_get_in_addr(const struct nlattr *nla)
{
	return *(__be32 *) nla_data(nla);
}

static inline struct in6_addr nla_get_in6_addr(const struct nlattr *nla)
{
	struct in6_addr tmp;

	nla_memcpy(&tmp, nla, sizeof(tmp));
	return tmp;
}
#endif

#ifndef HAVE_NLA_PUT_64BIT
static inline bool nla_need_padding_for_64bit(struct sk_buff *skb)
{
#ifndef CONFIG_HAVE_EFFICIENT_UNALIGNED_ACCESS
	/* The nlattr header is 4 bytes in size, that's why we test
	 * if the skb->data _is_ aligned.  A NOP attribute, plus
	 * nlattr header for next attribute, will make nla_data()
	 * 8-byte aligned.
	 */
	if (IS_ALIGNED((unsigned long)skb_tail_pointer(skb), 8))
		return true;
#endif
	return false;
}

static inline int nla_align_64bit(struct sk_buff *skb, int padattr)
{
	if (nla_need_padding_for_64bit(skb) &&
	    !nla_reserve(skb, padattr, 0))
		return -EMSGSIZE;

	return 0;
}

static inline int nla_total_size_64bit(int payload)
{
	return NLA_ALIGN(nla_attr_size(payload))
#ifndef CONFIG_HAVE_EFFICIENT_UNALIGNED_ACCESS
		+ NLA_ALIGN(nla_attr_size(0))
#endif
		;
}

#define nla_put_64bit rpl_nla_put_64bit
int rpl_nla_put_64bit(struct sk_buff *skb, int attrtype, int attrlen,
		  const void *data, int padattr);

#define __nla_put_64bit rpl___nla_put_64bit
void rpl___nla_put_64bit(struct sk_buff *skb, int attrtype, int attrlen,
                     const void *data, int padattr);

#define __nla_reserve_64bit rpl___nla_reserve_64bit
struct nlattr *rpl___nla_reserve_64bit(struct sk_buff *skb, int attrtype,
				   int attrlen, int padattr);

static inline int nla_put_u64_64bit(struct sk_buff *skb, int attrtype,
                                    u64 value, int padattr)
{
        return nla_put_64bit(skb, attrtype, sizeof(u64), &value, padattr);
}

#define nla_put_be64 rpl_nla_put_be64
static inline int nla_put_be64(struct sk_buff *skb, int attrtype, __be64 value,
                               int padattr)
{
        return nla_put_64bit(skb, attrtype, sizeof(__be64), &value, padattr);
}

#endif
#endif /* net/netlink.h */
