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

#include <config.h>

#include "conntrack-private.h"
#include "dp-packet.h"

enum other_state {
    OTHERS_FIRST,
    OTHERS_MULTIPLE,
    OTHERS_BIDIR,
};

struct conn_other {
    struct conn up;
    enum other_state state;
};

static const enum ct_timeout other_timeouts[] = {
    [OTHERS_FIRST] = CT_TM_OTHER_FIRST,
    [OTHERS_MULTIPLE] = CT_TM_OTHER_MULTIPLE,
    [OTHERS_BIDIR] = CT_TM_OTHER_BIDIR,
};

static struct conn_other *
conn_other_cast(const struct conn *conn)
{
    return CONTAINER_OF(conn, struct conn_other, up);
}

static enum ct_update_res
other_conn_update(struct conn *conn_, struct conntrack_bucket *ctb,
                  struct dp_packet *pkt OVS_UNUSED, bool reply, long long now)
{
    struct conn_other *conn = conn_other_cast(conn_);

    if (reply && conn->state != OTHERS_BIDIR) {
        conn->state = OTHERS_BIDIR;
    } else if (conn->state == OTHERS_FIRST) {
        conn->state = OTHERS_MULTIPLE;
    }

    conn_update_expiration(ctb, &conn->up, other_timeouts[conn->state], now);

    return CT_UPDATE_VALID;
}

static bool
other_valid_new(struct dp_packet *pkt OVS_UNUSED)
{
    return true;
}

static struct conn *
other_new_conn(struct conntrack_bucket *ctb, struct dp_packet *pkt OVS_UNUSED,
               long long now)
{
    struct conn_other *conn;

    conn = xzalloc(sizeof *conn);
    conn->state = OTHERS_FIRST;

    conn_init_expiration(ctb, &conn->up, other_timeouts[conn->state], now);

    return &conn->up;
}

struct ct_l4_proto ct_proto_other = {
    .new_conn = other_new_conn,
    .valid_new = other_valid_new,
    .conn_update = other_conn_update,
};
