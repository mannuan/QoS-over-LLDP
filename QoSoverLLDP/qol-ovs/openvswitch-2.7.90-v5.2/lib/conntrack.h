/*
 * Copyright (c) 2015, 2016 Nicira, Inc.
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

#ifndef CONNTRACK_H
#define CONNTRACK_H 1

#include <stdbool.h>

#include "latch.h"
#include "odp-netlink.h"
#include "openvswitch/hmap.h"
#include "openvswitch/list.h"
#include "openvswitch/thread.h"
#include "openvswitch/types.h"
#include "ovs-atomic.h"

/* Userspace connection tracker
 * ============================
 *
 * This is a connection tracking module that keeps all the state in userspace.
 *
 * Usage
 * =====
 *
 *     struct conntrack ct;
 *
 * Initialization:
 *
 *     conntrack_init(&ct);
 *
 * It is necessary to periodically issue a call to
 *
 *     conntrack_run(&ct);
 *
 * to allow the module to clean up expired connections.
 *
 * To send a group of packets through the connection tracker:
 *
 *     conntrack_execute(&ct, pkts, n_pkts, ...);
 *
 * Thread-safety
 * =============
 *
 * conntrack_execute() can be called by multiple threads simultaneoulsy.
 */

struct dp_packet_batch;

struct conntrack;

void conntrack_init(struct conntrack *);
void conntrack_destroy(struct conntrack *);

int conntrack_execute(struct conntrack *, struct dp_packet_batch *,
                      ovs_be16 dl_type, bool force, bool commit,
                      uint16_t zone, const uint32_t *setmark,
                      const struct ovs_key_ct_labels *setlabel,
                      const char *helper);

struct conntrack_dump {
    struct conntrack *ct;
    unsigned bucket;
    struct hmap_position bucket_pos;
    bool filter_zone;
    uint16_t zone;
};

struct ct_dpif_entry;

int conntrack_dump_start(struct conntrack *, struct conntrack_dump *,
                         const uint16_t *pzone);
int conntrack_dump_next(struct conntrack_dump *, struct ct_dpif_entry *);
int conntrack_dump_done(struct conntrack_dump *);

int conntrack_flush(struct conntrack *, const uint16_t *zone);

/* 'struct ct_lock' is a wrapper for an adaptive mutex.  It's useful to try
 * different types of locks (e.g. spinlocks) */

struct OVS_LOCKABLE ct_lock {
    struct ovs_mutex lock;
};

static inline void ct_lock_init(struct ct_lock *lock)
{
    ovs_mutex_init_adaptive(&lock->lock);
}

static inline void ct_lock_lock(struct ct_lock *lock)
    OVS_ACQUIRES(lock)
    OVS_NO_THREAD_SAFETY_ANALYSIS
{
    ovs_mutex_lock(&lock->lock);
}

static inline void ct_lock_unlock(struct ct_lock *lock)
    OVS_RELEASES(lock)
    OVS_NO_THREAD_SAFETY_ANALYSIS
{
    ovs_mutex_unlock(&lock->lock);
}

static inline void ct_lock_destroy(struct ct_lock *lock)
{
    ovs_mutex_destroy(&lock->lock);
}

/* Timeouts: all the possible timeout states passed to update_expiration()
 * are listed here. The name will be prefix by CT_TM_ and the value is in
 * milliseconds */
#define CT_TIMEOUTS \
    CT_TIMEOUT(TCP_FIRST_PACKET, 30 * 1000) \
    CT_TIMEOUT(TCP_OPENING, 30 * 1000) \
    CT_TIMEOUT(TCP_ESTABLISHED, 24 * 60 * 60 * 1000) \
    CT_TIMEOUT(TCP_CLOSING, 15 * 60 * 1000) \
    CT_TIMEOUT(TCP_FIN_WAIT, 45 * 1000) \
    CT_TIMEOUT(TCP_CLOSED, 30 * 1000) \
    CT_TIMEOUT(OTHER_FIRST, 60 * 1000) \
    CT_TIMEOUT(OTHER_MULTIPLE, 60 * 1000) \
    CT_TIMEOUT(OTHER_BIDIR, 30 * 1000) \
    CT_TIMEOUT(ICMP_FIRST, 60 * 1000) \
    CT_TIMEOUT(ICMP_REPLY, 30 * 1000)

/* The smallest of the above values: it is used as an upper bound for the
 * interval between two rounds of cleanup of expired entries */
#define CT_TM_MIN (30 * 1000)

#define CT_TIMEOUT(NAME, VAL) BUILD_ASSERT_DECL(VAL >= CT_TM_MIN);
    CT_TIMEOUTS
#undef CT_TIMEOUT

enum ct_timeout {
#define CT_TIMEOUT(NAME, VALUE) CT_TM_##NAME,
    CT_TIMEOUTS
#undef CT_TIMEOUT
    N_CT_TM
};

/* Locking:
 *
 * The connections are kept in different buckets, which are completely
 * independent. The connection bucket is determined by the hash of its key.
 *
 * Each bucket has two locks. Acquisition order is, from outermost to
 * innermost:
 *
 *    cleanup_mutex
 *    lock
 *
 * */
struct conntrack_bucket {
    /* Protects 'connections' and 'exp_lists'.  Used in the fast path */
    struct ct_lock lock;
    /* Contains the connections in the bucket, indexed by 'struct conn_key' */
    struct hmap connections OVS_GUARDED;
    /* For each possible timeout we have a list of connections. When the
     * timeout of a connection is updated, we move it to the back of the list.
     * Since the connection in a list have the same relative timeout, the list
     * will be ordered, with the oldest connections to the front. */
    struct ovs_list exp_lists[N_CT_TM] OVS_GUARDED;

    /* Protects 'next_cleanup'. Used to make sure that there's only one thread
     * performing the cleanup. */
    struct ovs_mutex cleanup_mutex;
    long long next_cleanup OVS_GUARDED;
};

#define CONNTRACK_BUCKETS_SHIFT 8
#define CONNTRACK_BUCKETS (1 << CONNTRACK_BUCKETS_SHIFT)

struct conntrack {
    /* Independent buckets containing the connections */
    struct conntrack_bucket buckets[CONNTRACK_BUCKETS];

    /* Salt for hashing a connection key. */
    uint32_t hash_basis;

    /* The thread performing periodic cleanup of the connection
     * tracker */
    pthread_t clean_thread;
    /* Latch to destroy the 'clean_thread' */
    struct latch clean_thread_exit;

    /* Number of connections currently in the connection tracker. */
    atomic_count n_conn;
    /* Connections limit. When this limit is reached, no new connection
     * will be accepted. */
    atomic_uint n_conn_limit;
};

#endif /* conntrack.h */
