/*
 * Copyright (c) 2015 Nicira, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <config.h>
#include "dpif-provider.h"

#include <errno.h>

#include "ct-dpif.h"
#include "openvswitch/vlog.h"

VLOG_DEFINE_THIS_MODULE(ct_dpif);

/* Declarations for conntrack entry formatting. */
struct flags {
    uint32_t flag;
    const char *name;
};

static void ct_dpif_format_ipproto(struct ds *, uint16_t ipproto);
static void ct_dpif_format_counters(struct ds *,
                                    const struct ct_dpif_counters *);
static void ct_dpif_format_timestamp(struct ds *,
                                     const struct ct_dpif_timestamp *);
static void ct_dpif_format_flags(struct ds *, const char *title,
                                 uint32_t flags, const struct flags *);
static void ct_dpif_format_protoinfo(struct ds *, const char *title,
                                     const struct ct_dpif_protoinfo *,
                                     bool verbose);
static void ct_dpif_format_helper(struct ds *, const char *title,
                                  const struct ct_dpif_helper *);

static const struct flags ct_dpif_status_flags[] = {
#define CT_DPIF_STATUS_FLAG(FLAG) { CT_DPIF_STATUS_##FLAG, #FLAG },
    CT_DPIF_STATUS_FLAGS
#undef CT_DPIF_STATUS_FLAG
    { 0, NULL } /* End marker. */
};

/* Dumping */

/* Start dumping the entries from the connection tracker used by 'dpif'.
 *
 * 'dump' must be the address of a pointer to a struct ct_dpif_dump_state,
 * which should be passed (unaltered) to ct_dpif_dump_{next,done}().
 *
 * If 'zone' is not NULL, it should point to an integer identifing a
 * conntrack zone to which the dump will be limited.  If it is NULL,
 * conntrack entries from all zones will be dumped.
 *
 * If there has been a problem the function returns a non-zero value
 * that represents the error.  Otherwise it returns zero. */
int
ct_dpif_dump_start(struct dpif *dpif, struct ct_dpif_dump_state **dump,
                   const uint16_t *zone)
{
    int err;

    err = (dpif->dpif_class->ct_dump_start
           ? dpif->dpif_class->ct_dump_start(dpif, dump, zone)
           : EOPNOTSUPP);

    if (!err) {
        (*dump)->dpif = dpif;
    }

    return err;
}

/* Dump one connection from a tracker, and put it in 'entry'.
 *
 * 'dump' should have been initialized by ct_dpif_dump_start().
 *
 * The function returns 0, if an entry has been dumped succesfully.
 * Otherwise it returns a non-zero value which can be:
 * - EOF: meaning that there are no more entries to dump.
 * - an error value.
 * In both cases, the user should call ct_dpif_dump_done(). */
int
ct_dpif_dump_next(struct ct_dpif_dump_state *dump, struct ct_dpif_entry *entry)
{
    struct dpif *dpif = dump->dpif;

    return (dpif->dpif_class->ct_dump_next
            ? dpif->dpif_class->ct_dump_next(dpif, dump, entry)
            : EOPNOTSUPP);
}

/* Free resources used by 'dump' */
int
ct_dpif_dump_done(struct ct_dpif_dump_state *dump)
{
    struct dpif *dpif = dump->dpif;

    return (dpif->dpif_class->ct_dump_done
            ? dpif->dpif_class->ct_dump_done(dpif, dump)
            : EOPNOTSUPP);
}

/* Flush the entries in the connection tracker used by 'dpif'.
 *
 * If 'zone' is not NULL, flush only the entries in '*zone'. */
int
ct_dpif_flush(struct dpif *dpif, const uint16_t *zone)
{
    if (zone) {
        VLOG_DBG("%s: ct_flush: %"PRIu16, dpif_name(dpif), *zone);
    } else {
        VLOG_DBG("%s: ct_flush: <all>", dpif_name(dpif));
    }

    return (dpif->dpif_class->ct_flush
            ? dpif->dpif_class->ct_flush(dpif, zone)
            : EOPNOTSUPP);
}

void
ct_dpif_entry_uninit(struct ct_dpif_entry *entry)
{
    if (entry) {
        if (entry->helper.name) {
            free(entry->helper.name);
        }
    }
}

void
ct_dpif_format_entry(const struct ct_dpif_entry *entry, struct ds *ds,
                     bool verbose, bool print_stats)
{
    ct_dpif_format_ipproto(ds, entry->tuple_orig.ip_proto);

    ds_put_cstr(ds, ",orig=(");
    ct_dpif_format_tuple(ds, &entry->tuple_orig);
    if (print_stats) {
        ct_dpif_format_counters(ds, &entry->counters_orig);
    }
    ds_put_cstr(ds, ")");

    ds_put_cstr(ds, ",reply=(");
    ct_dpif_format_tuple(ds, &entry->tuple_reply);
    if (print_stats) {
        ct_dpif_format_counters(ds, &entry->counters_reply);
    }
    ds_put_cstr(ds, ")");

    if (print_stats) {
        ct_dpif_format_timestamp(ds, &entry->timestamp);
    }
    if (verbose) {
        ds_put_format(ds, ",id=%"PRIu32, entry->id);
    }
    if (entry->zone) {
        ds_put_format(ds, ",zone=%"PRIu16, entry->zone);
    }
    if (verbose) {
        ct_dpif_format_flags(ds, ",status=", entry->status,
                             ct_dpif_status_flags);
    }
    if (print_stats) {
        ds_put_format(ds, ",timeout=%"PRIu32, entry->timeout);
    }
    if (entry->mark) {
        ds_put_format(ds, ",mark=%"PRIu32, entry->mark);
    }
    if (!ovs_u128_is_zero(entry->labels)) {
        ovs_be128 value;

        ds_put_cstr(ds, ",labels=");
        value = hton128(entry->labels);
        ds_put_hex(ds, &value, sizeof value);
    }
    ct_dpif_format_protoinfo(ds, ",protoinfo=", &entry->protoinfo, verbose);
    ct_dpif_format_helper(ds, ",helper=", &entry->helper);
    if (verbose && entry->tuple_master.l3_type != 0) {
        ds_put_cstr(ds, ",master=(");
        ct_dpif_format_tuple(ds, &entry->tuple_master);
        ds_put_cstr(ds, ")");
    }
}

static void
ct_dpif_format_ipproto(struct ds *ds, uint16_t ipproto)
{
    const char *name;

    name = (ipproto == IPPROTO_ICMP) ? "icmp"
        : (ipproto == IPPROTO_ICMPV6) ? "icmpv6"
        : (ipproto == IPPROTO_TCP) ? "tcp"
        : (ipproto == IPPROTO_UDP) ? "udp"
        : (ipproto == IPPROTO_SCTP) ? "sctp"
        : NULL;

    if (name) {
        ds_put_cstr(ds, name);
    } else {
        ds_put_format(ds, "%u", ipproto);
    }
}

static void
ct_dpif_format_counters(struct ds *ds, const struct ct_dpif_counters *counters)
{
    if (counters->packets || counters->bytes) {
        ds_put_format(ds, ",packets=%"PRIu64",bytes=%"PRIu64,
                      counters->packets, counters->bytes);
    }
}

static void
ct_dpif_format_timestamp(struct ds *ds,
                         const struct ct_dpif_timestamp *timestamp)
{
    if (timestamp->start || timestamp->stop) {
        ds_put_strftime_msec(ds, ",start=%Y-%m-%dT%H:%M:%S.###",
                             timestamp->start / UINT64_C(1000000), false);
        if (timestamp->stop) {
            ds_put_strftime_msec(ds, ",stop=%Y-%m-%dT%H:%M:%S.###",
                                 timestamp->stop / UINT64_C(1000000), false);
        }
    }
}

static void
ct_dpif_format_tuple_icmp(struct ds *ds, const struct ct_dpif_tuple *tuple)
{
    ds_put_format(ds, ",id=%u,type=%u,code=%u", ntohs(tuple->icmp_id),
                  tuple->icmp_type, tuple->icmp_code);
}

static void
ct_dpif_format_tuple_tp(struct ds *ds, const struct ct_dpif_tuple *tuple)
{
    ds_put_format(ds, ",sport=%u,dport=%u",
                  ntohs(tuple->src_port), ntohs(tuple->dst_port));
}

void
ct_dpif_format_tuple(struct ds *ds, const struct ct_dpif_tuple *tuple)
{
    if (tuple->l3_type == AF_INET) {
        ds_put_format(ds, "src="IP_FMT",dst="IP_FMT,
                      IP_ARGS(tuple->src.ip), IP_ARGS(tuple->dst.ip));
    } else if (tuple->l3_type == AF_INET6) {
        ds_put_cstr(ds, "src=");
        ipv6_format_addr(&tuple->src.in6, ds);
        ds_put_cstr(ds, ",dst=");
        ipv6_format_addr(&tuple->dst.in6, ds);
    } else {
        ds_put_format(ds, "Unsupported address family: %u. HEX:\n",
                      tuple->l3_type);
        ds_put_hex_dump(ds, tuple, sizeof *tuple, 0, false);
        return;
    }

    if (tuple->ip_proto == IPPROTO_ICMP
        || tuple->ip_proto == IPPROTO_ICMPV6) {
        ct_dpif_format_tuple_icmp(ds, tuple);
    } else {
        ct_dpif_format_tuple_tp(ds, tuple);
    }
}

static void
ct_dpif_format_flags(struct ds *ds, const char *title, uint32_t flags,
                     const struct flags *table)
{
    if (title) {
        ds_put_cstr(ds, title);
    }
    for (; table->name; table++) {
        if (flags & table->flag) {
            ds_put_format(ds, "%s|", table->name);
        }
    }
    ds_chomp(ds, '|');
}

static const struct flags tcp_flags[] = {
#define CT_DPIF_TCP_FLAG(FLAG)  { CT_DPIF_TCPF_##FLAG, #FLAG },
    CT_DPIF_TCP_FLAGS
#undef CT_DPIF_TCP_FLAG
    { 0, NULL } /* End marker. */
};

const char *ct_dpif_tcp_state_string[] = {
#define CT_DPIF_TCP_STATE(STATE) [CT_DPIF_TCPS_##STATE] = #STATE,
    CT_DPIF_TCP_STATES
#undef CT_DPIF_TCP_STATE
};

static void
ct_dpif_format_enum__(struct ds *ds, const char *title, unsigned int state,
                      const char *names[], unsigned int max)
{
    if (title) {
        ds_put_cstr(ds, title);
    }
    if (state < max) {
        ds_put_cstr(ds, names[state]);
    } else {
        ds_put_format(ds, "[%u]", state);
    }
}

#define ct_dpif_format_enum(DS, TITLE, STATE, NAMES) \
    ct_dpif_format_enum__((DS), (TITLE), (STATE), (NAMES), ARRAY_SIZE(NAMES))

static uint8_t
coalesce_tcp_state(uint8_t state)
{
    /* The Linux kernel connection tracker and the userspace view the
     * tcp states differently in some situations.  If we're formatting
     * the entry without being verbose, it is worth to adjust the
     * differences, to ease writing testcases. */
    switch (state) {
        case CT_DPIF_TCPS_FIN_WAIT_2:
            return CT_DPIF_TCPS_TIME_WAIT;
        case CT_DPIF_TCPS_SYN_RECV:
            return CT_DPIF_TCPS_ESTABLISHED;
        default:
            return state;
    }
}

static void
ct_dpif_format_protoinfo_tcp(struct ds *ds,
                             const struct ct_dpif_protoinfo *protoinfo)
{
    uint8_t tcp_state;

    /* We keep two separate tcp states, but we print just one. The Linux
     * kernel connection tracker internally keeps only one state, so
     * 'state_orig' and 'state_reply', will be the same. */
    tcp_state = MAX(protoinfo->tcp.state_orig, protoinfo->tcp.state_reply);

    tcp_state = coalesce_tcp_state(tcp_state);
    ct_dpif_format_enum(ds, "state=", tcp_state, ct_dpif_tcp_state_string);
}

static void
ct_dpif_format_protoinfo_tcp_verbose(struct ds *ds,
                                     const struct ct_dpif_protoinfo *protoinfo)
{
    ct_dpif_format_enum(ds, "state_orig=", protoinfo->tcp.state_orig,
                        ct_dpif_tcp_state_string);
    ct_dpif_format_enum(ds, ",state_reply=", protoinfo->tcp.state_reply,
                        ct_dpif_tcp_state_string);

    if (protoinfo->tcp.wscale_orig || protoinfo->tcp.wscale_reply) {
        ds_put_format(ds, ",wscale_orig=%u,wscale_reply=%u",
                      protoinfo->tcp.wscale_orig,
                      protoinfo->tcp.wscale_reply);
    }
    ct_dpif_format_flags(ds, ",flags_orig=", protoinfo->tcp.flags_orig,
                         tcp_flags);
    ct_dpif_format_flags(ds, ",flags_reply=", protoinfo->tcp.flags_reply,
                         tcp_flags);
}

static void
ct_dpif_format_protoinfo(struct ds *ds, const char *title,
                         const struct ct_dpif_protoinfo *protoinfo,
                         bool verbose)
{
    if (protoinfo->proto != 0) {
        if (title) {
            ds_put_format(ds, "%s(", title);
        }
        switch (protoinfo->proto) {
        case IPPROTO_TCP:
            if (verbose) {
                ct_dpif_format_protoinfo_tcp_verbose(ds, protoinfo);
            } else {
                ct_dpif_format_protoinfo_tcp(ds, protoinfo);
            }
            break;
        }
        if (title) {
            ds_put_cstr(ds, ")");
        }
    }
}

static void
ct_dpif_format_helper(struct ds *ds, const char *title,
                    const struct ct_dpif_helper *helper)
{
    if (helper->name) {
        if (title) {
            ds_put_cstr(ds, title);
        }
        ds_put_cstr(ds, helper->name);
    }
}
