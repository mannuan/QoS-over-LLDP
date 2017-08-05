/*
 * Copyright (c) 2009, 2010, 2011, 2012, 2013, 2014, 2015, 2016 Nicira, Inc.
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

#include <ctype.h>
#include <errno.h>
#include <getopt.h>
#include <limits.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "command-line.h"
#include "column.h"
#include "compiler.h"
#include "daemon.h"
#include "dirs.h"
#include "openvswitch/dynamic-string.h"
#include "fatal-signal.h"
#include "openvswitch/json.h"
#include "jsonrpc.h"
#include "lib/table.h"
#include "ovsdb.h"
#include "ovsdb-data.h"
#include "ovsdb-error.h"
#include "poll-loop.h"
#include "sort.h"
#include "svec.h"
#include "stream.h"
#include "stream-ssl.h"
#include "table.h"
#include "monitor.h"
#include "condition.h"
#include "timeval.h"
#include "unixctl.h"
#include "util.h"
#include "openvswitch/vlog.h"

VLOG_DEFINE_THIS_MODULE(ovsdb_client);

enum args_needed {
    NEED_NONE,            /* No JSON-RPC connection or database name needed. */
    NEED_RPC,             /* JSON-RPC connection needed. */
    NEED_DATABASE         /* JSON-RPC connection and database name needed. */
};

struct ovsdb_client_command {
    const char *name;
    enum args_needed need;
    int min_args;
    int max_args;
    void (*handler)(struct jsonrpc *rpc, const char *database,
                    int argc, char *argv[]);
};

/* --timestamp: Print a timestamp before each update on "monitor" command? */
static bool timestamp;

/* Format for table output. */
static struct table_style table_style = TABLE_STYLE_DEFAULT;

static const struct ovsdb_client_command *get_all_commands(void);

OVS_NO_RETURN static void usage(void);
static void parse_options(int argc, char *argv[]);
static struct jsonrpc *open_jsonrpc(const char *server);
static void fetch_dbs(struct jsonrpc *, struct svec *dbs);

int
main(int argc, char *argv[])
{
    const struct ovsdb_client_command *command;
    char *database;
    struct jsonrpc *rpc;

    ovs_cmdl_proctitle_init(argc, argv);
    set_program_name(argv[0]);
    service_start(&argc, &argv);
    parse_options(argc, argv);
    fatal_ignore_sigpipe();

    daemon_become_new_user(false);
    if (optind >= argc) {
        ovs_fatal(0, "missing command name; use --help for help");
    }

    for (command = get_all_commands(); ; command++) {
        if (!command->name) {
            VLOG_FATAL("unknown command '%s'; use --help for help",
                       argv[optind]);
        } else if (!strcmp(command->name, argv[optind])) {
            break;
        }
    }
    optind++;

    if (command->need != NEED_NONE) {
        if (argc - optind > command->min_args
            && (isalpha((unsigned char) argv[optind][0])
                && strchr(argv[optind], ':'))) {
            rpc = open_jsonrpc(argv[optind++]);
        } else {
            char *sock = xasprintf("unix:%s/db.sock", ovs_rundir());
            rpc = open_jsonrpc(sock);
            free(sock);
        }
    } else {
        rpc = NULL;
    }

    if (command->need == NEED_DATABASE) {
        struct svec dbs;

        svec_init(&dbs);
        fetch_dbs(rpc, &dbs);
        if (argc - optind > command->min_args
            && svec_contains(&dbs, argv[optind])) {
            database = xstrdup(argv[optind++]);
        } else if (dbs.n == 1) {
            database = xstrdup(dbs.names[0]);
        } else if (svec_contains(&dbs, "Open_vSwitch")) {
            database = xstrdup("Open_vSwitch");
        } else {
            jsonrpc_close(rpc);
            ovs_fatal(0, "no default database for `%s' command, please "
                      "specify a database name", command->name);
        }
        svec_destroy(&dbs);
    } else {
        database = NULL;
    }

    if (argc - optind < command->min_args ||
        argc - optind > command->max_args) {
        free(database);
        VLOG_FATAL("invalid syntax for '%s' (use --help for help)",
                    command->name);
    }

    command->handler(rpc, database, argc - optind, argv + optind);

    free(database);
    jsonrpc_close(rpc);

    if (ferror(stdout)) {
        VLOG_FATAL("write to stdout failed");
    }
    if (ferror(stderr)) {
        VLOG_FATAL("write to stderr failed");
    }

    return 0;
}

static void
parse_options(int argc, char *argv[])
{
    enum {
        OPT_BOOTSTRAP_CA_CERT = UCHAR_MAX + 1,
        OPT_TIMESTAMP,
        VLOG_OPTION_ENUMS,
        DAEMON_OPTION_ENUMS,
        TABLE_OPTION_ENUMS,
        SSL_OPTION_ENUMS,
    };
    static const struct option long_options[] = {
        {"help", no_argument, NULL, 'h'},
        {"version", no_argument, NULL, 'V'},
        {"timestamp", no_argument, NULL, OPT_TIMESTAMP},
        VLOG_LONG_OPTIONS,
        DAEMON_LONG_OPTIONS,
#ifdef HAVE_OPENSSL
        {"bootstrap-ca-cert", required_argument, NULL, OPT_BOOTSTRAP_CA_CERT},
        STREAM_SSL_LONG_OPTIONS,
#endif
        TABLE_LONG_OPTIONS,
        {NULL, 0, NULL, 0},
    };
    char *short_options = ovs_cmdl_long_options_to_short_options(long_options);

    table_style.format = TF_TABLE;

    for (;;) {
        int c;

        c = getopt_long(argc, argv, short_options, long_options, NULL);
        if (c == -1) {
            break;
        }

        switch (c) {
        case 'h':
            usage();

        case 'V':
            ovs_print_version(0, 0);
            exit(EXIT_SUCCESS);

        VLOG_OPTION_HANDLERS
        DAEMON_OPTION_HANDLERS
        TABLE_OPTION_HANDLERS(&table_style)
        STREAM_SSL_OPTION_HANDLERS

        case OPT_BOOTSTRAP_CA_CERT:
            stream_ssl_set_ca_cert_file(optarg, true);
            break;

        case OPT_TIMESTAMP:
            timestamp = true;
            break;

        case '?':
            exit(EXIT_FAILURE);

        case 0:
            /* getopt_long() already set the value for us. */
            break;

        default:
            abort();
        }
    }
    free(short_options);
}

static void
usage(void)
{
    printf("%s: Open vSwitch database JSON-RPC client\n"
           "usage: %s [OPTIONS] COMMAND [ARG...]\n"
           "\nValid commands are:\n"
           "\n  list-dbs [SERVER]\n"
           "    list databases available on SERVER\n"
           "\n  get-schema [SERVER] [DATABASE]\n"
           "    retrieve schema for DATABASE from SERVER\n"
           "\n  get-schema-version [SERVER] [DATABASE]\n"
           "    retrieve schema for DATABASE from SERVER and report only its\n"
           "    version number on stdout\n"
           "\n  list-tables [SERVER] [DATABASE]\n"
           "    list tables for DATABASE on SERVER\n"
           "\n  list-columns [SERVER] [DATABASE] [TABLE]\n"
           "    list columns in TABLE (or all tables) in DATABASE on SERVER\n"
           "\n  transact [SERVER] TRANSACTION\n"
           "    run TRANSACTION (a JSON array of operations) on SERVER\n"
           "    and print the results as JSON on stdout\n"
           "\n  monitor [SERVER] [DATABASE] TABLE [COLUMN,...]...\n"
           "    monitor contents of COLUMNs in TABLE in DATABASE on SERVER.\n"
           "    COLUMNs may include !initial, !insert, !delete, !modify\n"
           "    to avoid seeing the specified kinds of changes.\n"
           "\n  monitor-cond [SERVER] [DATABASE] CONDITION TABLE [COLUMN,...]...\n"
           "    monitor contents that match CONDITION of COLUMNs in TABLE in\n"
           "    DATABASE on SERVER.\n"
           "    COLUMNs may include !initial, !insert, !delete, !modify\n"
           "    to avoid seeing the specified kinds of changes.\n"
           "\n  monitor [SERVER] [DATABASE] ALL\n"
           "    monitor all changes to all columns in all tables\n"
           "    in DATBASE on SERVER.\n"
           "\n  dump [SERVER] [DATABASE]\n"
           "    dump contents of DATABASE on SERVER to stdout\n"
           "\n  lock [SERVER] LOCK\n"
           "    create or wait for LOCK in SERVER\n"
           "\n  steal [SERVER] LOCK\n"
           "    steal LOCK from SERVER\n"
           "\n  unlock [SERVER] LOCK\n"
           "    unlock LOCK from SERVER\n"
           "\nThe default SERVER is unix:%s/db.sock.\n"
           "The default DATABASE is Open_vSwitch.\n",
           program_name, program_name, ovs_rundir());
    stream_usage("SERVER", true, true, true);
    printf("\nOutput formatting options:\n"
           "  -f, --format=FORMAT         set output formatting to FORMAT\n"
           "                              (\"table\", \"html\", \"csv\", "
           "or \"json\")\n"
           "  --no-headings               omit table heading row\n"
           "  --pretty                    pretty-print JSON in output\n"
           "  --timestamp                 timestamp \"monitor\" output");
    daemon_usage();
    vlog_usage();
    printf("\nOther options:\n"
           "  -h, --help                  display this help message\n"
           "  -V, --version               display version information\n");
    exit(EXIT_SUCCESS);
}

static void
check_txn(int error, struct jsonrpc_msg **reply_)
{
    struct jsonrpc_msg *reply = *reply_;

    if (error) {
        ovs_fatal(error, "transaction failed");
    }

    if (reply->error) {
        ovs_fatal(error, "transaction returned error: %s",
                  json_to_string(reply->error, table_style.json_flags));
    }
}

static struct json *
parse_json(const char *s)
{
    struct json *json = json_from_string(s);
    if (json->type == JSON_STRING) {
        ovs_fatal(0, "\"%s\": %s", s, json->u.string);
    }
    return json;
}

static struct jsonrpc *
open_jsonrpc(const char *server)
{
    struct stream *stream;
    int error;

    error = stream_open_block(jsonrpc_stream_open(server, &stream,
                              DSCP_DEFAULT), &stream);
    if (error == EAFNOSUPPORT) {
        struct pstream *pstream;

        error = jsonrpc_pstream_open(server, &pstream, DSCP_DEFAULT);
        if (error) {
            ovs_fatal(error, "failed to connect or listen to \"%s\"", server);
        }

        VLOG_INFO("%s: waiting for connection...", server);
        error = pstream_accept_block(pstream, &stream);
        if (error) {
            ovs_fatal(error, "failed to accept connection on \"%s\"", server);
        }

        pstream_close(pstream);
    } else if (error) {
        ovs_fatal(error, "failed to connect to \"%s\"", server);
    }

    return jsonrpc_open(stream);
}

static void
print_json(struct json *json)
{
    char *string = json_to_string(json, table_style.json_flags);
    fputs(string, stdout);
    free(string);
}

static void
print_and_free_json(struct json *json)
{
    print_json(json);
    json_destroy(json);
}

static void
check_ovsdb_error(struct ovsdb_error *error)
{
    if (error) {
        ovs_fatal(0, "%s", ovsdb_error_to_string(error));
    }
}

static struct ovsdb_schema *
fetch_schema(struct jsonrpc *rpc, const char *database)
{
    struct jsonrpc_msg *request, *reply;
    struct ovsdb_schema *schema;

    request = jsonrpc_create_request("get_schema",
                                     json_array_create_1(
                                         json_string_create(database)),
                                     NULL);
    check_txn(jsonrpc_transact_block(rpc, request, &reply), &reply);
    check_ovsdb_error(ovsdb_schema_from_json(reply->result, &schema));
    jsonrpc_msg_destroy(reply);

    return schema;
}

static void
fetch_dbs(struct jsonrpc *rpc, struct svec *dbs)
{
    struct jsonrpc_msg *request, *reply;
    size_t i;

    request = jsonrpc_create_request("list_dbs", json_array_create_empty(),
                                     NULL);

    check_txn(jsonrpc_transact_block(rpc, request, &reply), &reply);
    if (reply->result->type != JSON_ARRAY) {
        ovs_fatal(0, "list_dbs response is not array");
    }

    for (i = 0; i < reply->result->u.array.n; i++) {
        const struct json *name = reply->result->u.array.elems[i];

        if (name->type != JSON_STRING) {
            ovs_fatal(0, "list_dbs response %"PRIuSIZE" is not string", i);
        }
        svec_add(dbs, name->u.string);
    }
    jsonrpc_msg_destroy(reply);
    svec_sort(dbs);
}

static void
do_list_dbs(struct jsonrpc *rpc, const char *database OVS_UNUSED,
            int argc OVS_UNUSED, char *argv[] OVS_UNUSED)
{
    const char *db_name;
    struct svec dbs;
    size_t i;

    svec_init(&dbs);
    fetch_dbs(rpc, &dbs);
    SVEC_FOR_EACH (i, db_name, &dbs) {
        puts(db_name);
    }
    svec_destroy(&dbs);
}

static void
do_get_schema(struct jsonrpc *rpc, const char *database,
              int argc OVS_UNUSED, char *argv[] OVS_UNUSED)
{
    struct ovsdb_schema *schema = fetch_schema(rpc, database);
    print_and_free_json(ovsdb_schema_to_json(schema));
    ovsdb_schema_destroy(schema);
}

static void
do_get_schema_version(struct jsonrpc *rpc, const char *database,
                      int argc OVS_UNUSED, char *argv[] OVS_UNUSED)
{
    struct ovsdb_schema *schema = fetch_schema(rpc, database);
    puts(schema->version);
    ovsdb_schema_destroy(schema);
}

static void
do_list_tables(struct jsonrpc *rpc, const char *database,
               int argc OVS_UNUSED, char *argv[] OVS_UNUSED)
{
    struct ovsdb_schema *schema;
    struct shash_node *node;
    struct table t;

    schema = fetch_schema(rpc, database);
    table_init(&t);
    table_add_column(&t, "Table");
    SHASH_FOR_EACH (node, &schema->tables) {
        struct ovsdb_table_schema *ts = node->data;

        table_add_row(&t);
        table_add_cell(&t)->text = xstrdup(ts->name);
    }
    ovsdb_schema_destroy(schema);
    table_print(&t, &table_style);
    table_destroy(&t);
}

static void
do_list_columns(struct jsonrpc *rpc, const char *database,
                int argc OVS_UNUSED, char *argv[])
{
    const char *table_name = argv[0];
    struct ovsdb_schema *schema;
    struct shash_node *table_node;
    struct table t;

    schema = fetch_schema(rpc, database);
    table_init(&t);
    if (!table_name) {
        table_add_column(&t, "Table");
    }
    table_add_column(&t, "Column");
    table_add_column(&t, "Type");
    SHASH_FOR_EACH (table_node, &schema->tables) {
        struct ovsdb_table_schema *ts = table_node->data;

        if (!table_name || !strcmp(table_name, ts->name)) {
            struct shash_node *column_node;

            SHASH_FOR_EACH (column_node, &ts->columns) {
                const struct ovsdb_column *column = column_node->data;

                table_add_row(&t);
                if (!table_name) {
                    table_add_cell(&t)->text = xstrdup(ts->name);
                }
                table_add_cell(&t)->text = xstrdup(column->name);
                table_add_cell(&t)->json = ovsdb_type_to_json(&column->type);
            }
        }
    }
    ovsdb_schema_destroy(schema);
    table_print(&t, &table_style);
    table_destroy(&t);
}

static void
do_transact(struct jsonrpc *rpc, const char *database OVS_UNUSED,
            int argc OVS_UNUSED, char *argv[])
{
    struct jsonrpc_msg *request, *reply;
    struct json *transaction;

    transaction = parse_json(argv[0]);

    request = jsonrpc_create_request("transact", transaction, NULL);
    check_txn(jsonrpc_transact_block(rpc, request, &reply), &reply);
    print_json(reply->result);
    putchar('\n');
    jsonrpc_msg_destroy(reply);
}

/* "monitor" command. */

struct monitored_table {
    struct ovsdb_table_schema *table;
    struct ovsdb_column_set columns;
};

static void
monitor_print_row(struct json *row, const char *type, const char *uuid,
                  const struct ovsdb_column_set *columns, struct table *t)
{
    size_t i;

    if (!row) {
        ovs_error(0, "missing %s row", type);
        return;
    } else if (row->type != JSON_OBJECT) {
        ovs_error(0, "<row> is not object");
        return;
    }

    table_add_row(t);
    table_add_cell(t)->text = xstrdup(uuid);
    table_add_cell(t)->text = xstrdup(type);
    for (i = 0; i < columns->n_columns; i++) {
        const struct ovsdb_column *column = columns->columns[i];
        struct json *value = shash_find_data(json_object(row), column->name);
        struct cell *cell = table_add_cell(t);
        if (value) {
            cell->json = json_clone(value);
            cell->type = &column->type;
        }
    }
}

static void
monitor_print_table(struct json *table_update,
                    const struct monitored_table *mt, char *caption,
                    bool initial)
{
    const struct ovsdb_table_schema *table = mt->table;
    const struct ovsdb_column_set *columns = &mt->columns;
    struct shash_node *node;
    struct table t;
    size_t i;

    if (table_update->type != JSON_OBJECT) {
        ovs_error(0, "<table-update> for table %s is not object", table->name);
        return;
    }

    table_init(&t);
    table_set_timestamp(&t, timestamp);
    table_set_caption(&t, caption);

    table_add_column(&t, "row");
    table_add_column(&t, "action");
    for (i = 0; i < columns->n_columns; i++) {
        table_add_column(&t, "%s", columns->columns[i]->name);
    }
    SHASH_FOR_EACH (node, json_object(table_update)) {
        struct json *row_update = node->data;
        struct json *old, *new;

        if (row_update->type != JSON_OBJECT) {
            ovs_error(0, "<row-update> is not object");
            continue;
        }
        old = shash_find_data(json_object(row_update), "old");
        new = shash_find_data(json_object(row_update), "new");
        if (initial) {
            monitor_print_row(new, "initial", node->name, columns, &t);
        } else if (!old) {
            monitor_print_row(new, "insert", node->name, columns, &t);
        } else if (!new) {
            monitor_print_row(old, "delete", node->name, columns, &t);
        } else {
            monitor_print_row(old, "old", node->name, columns, &t);
            monitor_print_row(new, "new", "", columns, &t);
        }
    }
    table_print(&t, &table_style);
    table_destroy(&t);
}

static void
monitor_print(struct json *table_updates,
              const struct monitored_table *mts, size_t n_mts,
              bool initial)
{
    size_t i;

    if (table_updates->type != JSON_OBJECT) {
        ovs_error(0, "<table-updates> is not object");
        return;
    }

    for (i = 0; i < n_mts; i++) {
        const struct monitored_table *mt = &mts[i];
        struct json *table_update = shash_find_data(json_object(table_updates),
                                                    mt->table->name);
        if (table_update) {
            monitor_print_table(table_update, mt,
                                n_mts > 1 ? xstrdup(mt->table->name) : NULL,
                                initial);
        }
    }
}

static void
monitor2_print_row(struct json *row, const char *type, const char *uuid,
                   const struct ovsdb_column_set *columns, struct table *t)
{
    if (!strcmp(type, "delete")) {
        if (row->type != JSON_NULL) {
            ovs_error(0, "delete method does not expect <row>");
            return;
        }

        table_add_row(t);
        table_add_cell(t)->text = xstrdup(uuid);
        table_add_cell(t)->text = xstrdup(type);
    } else {
        if (!row || row->type != JSON_OBJECT) {
            ovs_error(0, "<row> is not object");
            return;
        }
        monitor_print_row(row, type, uuid, columns, t);
    }
}

static void
monitor2_print_table(struct json *table_update2,
                    const struct monitored_table *mt, char *caption)
{
    const struct ovsdb_table_schema *table = mt->table;
    const struct ovsdb_column_set *columns = &mt->columns;
    struct shash_node *node;
    struct table t;

    if (table_update2->type != JSON_OBJECT) {
        ovs_error(0, "<table-update> for table %s is not object", table->name);
        return;
    }

    table_init(&t);
    table_set_timestamp(&t, timestamp);
    table_set_caption(&t, caption);

    table_add_column(&t, "row");
    table_add_column(&t, "action");
    for (size_t i = 0; i < columns->n_columns; i++) {
        table_add_column(&t, "%s", columns->columns[i]->name);
    }
    SHASH_FOR_EACH (node, json_object(table_update2)) {
        struct json *row_update2 = node->data;
        const char *operation;
        struct json *row;
        const char *ops[] = {"delete", "initial", "modify", "insert"};

        if (row_update2->type != JSON_OBJECT) {
            ovs_error(0, "<row-update2> is not object");
            continue;
        }

        /* row_update2 contains one of objects indexed by ops[] */
        for (int i = 0; i < ARRAY_SIZE(ops); i++) {
            operation = ops[i];
            row = shash_find_data(json_object(row_update2), operation);

            if (row) {
                monitor2_print_row(row, operation, node->name, columns, &t);
                break;
            }
        }
    }
    table_print(&t, &table_style);
    table_destroy(&t);
}

static void
monitor2_print(struct json *table_updates2,
               const struct monitored_table *mts, size_t n_mts)
{
    size_t i;

    if (table_updates2->type != JSON_OBJECT) {
        ovs_error(0, "<table-updates2> is not object");
        return;
    }

    for (i = 0; i < n_mts; i++) {
        const struct monitored_table *mt = &mts[i];
        struct json *table_update = shash_find_data(
                                        json_object(table_updates2),
                                        mt->table->name);
        if (table_update) {
            monitor2_print_table(table_update, mt,
                                n_mts > 1 ? xstrdup(mt->table->name) : NULL);
        }
    }
}

static void
add_column(const char *server, const struct ovsdb_column *column,
           struct ovsdb_column_set *columns, struct json *columns_json)
{
    if (ovsdb_column_set_contains(columns, column->index)) {
        ovs_fatal(0, "%s: column \"%s\" mentioned multiple times",
                  server, column->name);
    }
    ovsdb_column_set_add(columns, column);
    json_array_add(columns_json, json_string_create(column->name));
}

static struct json *
parse_monitor_columns(char *arg, const char *server, const char *database,
                      const struct ovsdb_table_schema *table,
                      struct ovsdb_column_set *columns)
{
    bool initial, insert, delete, modify;
    struct json *mr, *columns_json;
    char *save_ptr = NULL;
    char *token;

    mr = json_object_create();
    columns_json = json_array_create_empty();
    json_object_put(mr, "columns", columns_json);

    initial = insert = delete = modify = true;
    for (token = strtok_r(arg, ",", &save_ptr); token != NULL;
         token = strtok_r(NULL, ",", &save_ptr)) {
        if (!strcmp(token, "!initial")) {
            initial = false;
        } else if (!strcmp(token, "!insert")) {
            insert = false;
        } else if (!strcmp(token, "!delete")) {
            delete = false;
        } else if (!strcmp(token, "!modify")) {
            modify = false;
        } else {
            const struct ovsdb_column *column;

            column = ovsdb_table_schema_get_column(table, token);
            if (!column) {
                ovs_fatal(0, "%s: table \"%s\" in %s does not have a "
                          "column named \"%s\"",
                          server, table->name, database, token);
            }
            add_column(server, column, columns, columns_json);
        }
    }

    if (columns_json->u.array.n == 0) {
        const struct shash_node **nodes;
        size_t i, n;

        n = shash_count(&table->columns);
        nodes = shash_sort(&table->columns);
        for (i = 0; i < n; i++) {
            const struct ovsdb_column *column = nodes[i]->data;
            if (column->index != OVSDB_COL_UUID
                && column->index != OVSDB_COL_VERSION) {
                add_column(server, column, columns, columns_json);
            }
        }
        free(nodes);

        add_column(server, ovsdb_table_schema_get_column(table, "_version"),
                   columns, columns_json);
    }

    if (!initial || !insert || !delete || !modify) {
        struct json *select = json_object_create();
        json_object_put(select, "initial", json_boolean_create(initial));
        json_object_put(select, "insert", json_boolean_create(insert));
        json_object_put(select, "delete", json_boolean_create(delete));
        json_object_put(select, "modify", json_boolean_create(modify));
        json_object_put(mr, "select", select);
    }

    return mr;
}

static void
ovsdb_client_exit(struct unixctl_conn *conn, int argc OVS_UNUSED,
                  const char *argv[] OVS_UNUSED, void *exiting_)
{
    bool *exiting = exiting_;
    *exiting = true;
    unixctl_command_reply(conn, NULL);
}

static void
ovsdb_client_block(struct unixctl_conn *conn, int argc OVS_UNUSED,
                   const char *argv[] OVS_UNUSED, void *blocked_)
{
    bool *blocked = blocked_;

    if (!*blocked) {
        *blocked = true;
        unixctl_command_reply(conn, NULL);
    } else {
        unixctl_command_reply(conn, "already blocking");
    }
}

static void
ovsdb_client_unblock(struct unixctl_conn *conn, int argc OVS_UNUSED,
                     const char *argv[] OVS_UNUSED, void *blocked_)
{
    bool *blocked = blocked_;

    if (*blocked) {
        *blocked = false;
        unixctl_command_reply(conn, NULL);
    } else {
        unixctl_command_reply(conn, "already unblocked");
    }
}

static void
ovsdb_client_cond_change(struct unixctl_conn *conn, int argc OVS_UNUSED,
                     const char *argv[], void *rpc_)
{
    struct jsonrpc *rpc = rpc_;
    struct json *monitor_cond_update_requests = json_object_create();
    struct json *monitor_cond_update_request = json_object_create();
    struct json *params;
    struct jsonrpc_msg *request;

    json_object_put(monitor_cond_update_request, "where",
                    json_from_string(argv[2]));
    json_object_put(monitor_cond_update_requests,
                    argv[1],
                    json_array_create_1(monitor_cond_update_request));

    params = json_array_create_3(json_null_create(),json_null_create(),
                                 monitor_cond_update_requests);

    request = jsonrpc_create_request("monitor_cond_change", params, NULL);
    jsonrpc_send(rpc, request);

    VLOG_DBG("cond change %s %s", argv[1], argv[2]);
    unixctl_command_reply(conn, "condiiton changed");
}

static void
add_monitored_table(int argc, char *argv[],
                    const char *server, const char *database,
                    struct json *condition,
                    struct ovsdb_table_schema *table,
                    struct json *monitor_requests,
                    struct monitored_table **mts,
                    size_t *n_mts, size_t *allocated_mts)
{
    struct json *monitor_request_array, *mr;
    struct monitored_table *mt;

    if (*n_mts >= *allocated_mts) {
        *mts = x2nrealloc(*mts, allocated_mts, sizeof **mts);
    }
    mt = &(*mts)[(*n_mts)++];
    mt->table = table;
    ovsdb_column_set_init(&mt->columns);

    monitor_request_array = json_array_create_empty();
    if (argc > 1) {
        int i;

        for (i = 1; i < argc; i++) {
            mr = parse_monitor_columns(argv[i], server, database, table,
                                       &mt->columns);
            if (i == 1 && condition) {
                json_object_put(mr, "where", condition);
            }
            json_array_add(monitor_request_array, mr);
        }
    } else {
        /* Allocate a writable empty string since parse_monitor_columns()
         * is going to strtok() it and that's risky with literal "". */
        char empty[] = "";

        mr = parse_monitor_columns(empty, server, database,
                                   table, &mt->columns);
        if (condition) {
            json_object_put(mr, "where", condition);
        }
        json_array_add(monitor_request_array, mr);
    }

    json_object_put(monitor_requests, table->name, monitor_request_array);
}

static void
destroy_monitored_table(struct monitored_table *mts, size_t n)
{
    int i;

    for (i = 0; i < n; i++) {
        struct monitored_table *mt = &mts[i];
        ovsdb_column_set_destroy(&mt->columns);
    }

    free(mts);
}

static void
do_monitor__(struct jsonrpc *rpc, const char *database,
             enum ovsdb_monitor_version version,
             int argc, char *argv[], struct json *condition)
{
    const char *server = jsonrpc_get_name(rpc);
    const char *table_name = argv[0];
    struct unixctl_server *unixctl;
    struct ovsdb_schema *schema;
    struct jsonrpc_msg *request;
    struct json *monitor, *monitor_requests, *request_id;
    bool exiting = false;
    bool blocked = false;

    struct monitored_table *mts;
    size_t n_mts, allocated_mts;

    ovs_assert(version < OVSDB_MONITOR_VERSION_MAX);

    daemon_save_fd(STDOUT_FILENO);
    daemonize_start(false);
    if (get_detach()) {
        int error;

        error = unixctl_server_create(NULL, &unixctl);
        if (error) {
            ovs_fatal(error, "failed to create unixctl server");
        }

        unixctl_command_register("exit", "", 0, 0,
                                 ovsdb_client_exit, &exiting);
        unixctl_command_register("ovsdb-client/block", "", 0, 0,
                                 ovsdb_client_block, &blocked);
        unixctl_command_register("ovsdb-client/unblock", "", 0, 0,
                                 ovsdb_client_unblock, &blocked);
        unixctl_command_register("ovsdb-client/cond_change", "TABLE COND", 2, 2,
                                 ovsdb_client_cond_change, rpc);
    } else {
        unixctl = NULL;
    }

    schema = fetch_schema(rpc, database);

    monitor_requests = json_object_create();

    mts = NULL;
    n_mts = allocated_mts = 0;
    if (strcmp(table_name, "ALL")) {
        struct ovsdb_table_schema *table;

        table = shash_find_data(&schema->tables, table_name);
        if (!table) {
            ovs_fatal(0, "%s: %s does not have a table named \"%s\"",
                      server, database, table_name);
        }

        add_monitored_table(argc, argv, server, database, condition, table,
                            monitor_requests, &mts, &n_mts, &allocated_mts);
    } else {
        size_t n = shash_count(&schema->tables);
        const struct shash_node **nodes = shash_sort(&schema->tables);
        size_t i;

        if (condition) {
            ovs_fatal(0, "ALL tables are not allowed with condition");
        }

        for (i = 0; i < n; i++) {
            struct ovsdb_table_schema *table = nodes[i]->data;

            add_monitored_table(argc, argv, server, database, NULL, table,
                                monitor_requests,
                                &mts, &n_mts, &allocated_mts);
        }
        free(nodes);
    }

    monitor = json_array_create_3(json_string_create(database),
                                  json_null_create(), monitor_requests);
    const char *method = version == OVSDB_MONITOR_V2 ? "monitor_cond"
                                                     : "monitor";

    request = jsonrpc_create_request(method, monitor, NULL);
    request_id = json_clone(request->id);
    jsonrpc_send(rpc, request);

    for (;;) {
        unixctl_server_run(unixctl);
        while (!blocked) {
            struct jsonrpc_msg *msg;
            int error;

            error = jsonrpc_recv(rpc, &msg);
            if (error == EAGAIN) {
                break;
            } else if (error) {
                ovs_fatal(error, "%s: receive failed", server);
            }

            if (msg->type == JSONRPC_REQUEST && !strcmp(msg->method, "echo")) {
                jsonrpc_send(rpc, jsonrpc_create_reply(json_clone(msg->params),
                                                       msg->id));
            } else if (msg->type == JSONRPC_REPLY
                       && json_equal(msg->id, request_id)) {
                switch(version) {
                case OVSDB_MONITOR_V1:
                    monitor_print(msg->result, mts, n_mts, true);
                    break;
                case OVSDB_MONITOR_V2:
                    monitor2_print(msg->result, mts, n_mts);
                    break;
                case OVSDB_MONITOR_VERSION_MAX:
                default:
                    OVS_NOT_REACHED();
                }
                fflush(stdout);
                daemonize_complete();
            } else if (msg->type == JSONRPC_NOTIFY
                       && !strcmp(msg->method, "update")) {
                struct json *params = msg->params;
                if (params->type == JSON_ARRAY
                    && params->u.array.n == 2
                    && params->u.array.elems[0]->type == JSON_NULL) {
                    monitor_print(params->u.array.elems[1], mts, n_mts, false);
                    fflush(stdout);
                }
            } else if (msg->type == JSONRPC_NOTIFY
                       && version == OVSDB_MONITOR_V2
                       && !strcmp(msg->method, "update2")) {
                struct json *params = msg->params;
                if (params->type == JSON_ARRAY
                    && params->u.array.n == 2
                    && params->u.array.elems[0]->type == JSON_NULL) {
                    monitor2_print(params->u.array.elems[1], mts, n_mts);
                    fflush(stdout);
                }
            }
            jsonrpc_msg_destroy(msg);
        }

        if (exiting) {
            break;
        }

        jsonrpc_run(rpc);
        jsonrpc_wait(rpc);
        if (!blocked) {
            jsonrpc_recv_wait(rpc);
        }
        unixctl_server_wait(unixctl);
        poll_block();
    }

    json_destroy(request_id);
    unixctl_server_destroy(unixctl);
    ovsdb_schema_destroy(schema);
    destroy_monitored_table(mts, n_mts);
}

static void
do_monitor(struct jsonrpc *rpc, const char *database,
           int argc, char *argv[])
{
    do_monitor__(rpc, database, OVSDB_MONITOR_V1, argc, argv, NULL);
}

static void
do_monitor_cond(struct jsonrpc *rpc, const char *database,
           int argc, char *argv[])
{
    struct ovsdb_condition cnd;
    struct json *condition = NULL;
    struct ovsdb_schema *schema;
    struct ovsdb_table_schema *table;
    const char *table_name = argv[1];

    ovs_assert(argc > 1);
    schema = fetch_schema(rpc, database);
    table = shash_find_data(&schema->tables, table_name);
    if (!table) {
        ovs_fatal(0, "%s does not have a table named \"%s\"",
                  database, table_name);
    }
    condition = parse_json(argv[0]);
    check_ovsdb_error(ovsdb_condition_from_json(table, condition,
                                                    NULL, &cnd));
    ovsdb_condition_destroy(&cnd);
    do_monitor__(rpc, database, OVSDB_MONITOR_V2, --argc, ++argv, condition);
    ovsdb_schema_destroy(schema);
}

struct dump_table_aux {
    struct ovsdb_datum **data;
    const struct ovsdb_column **columns;
    size_t n_columns;
};

static int
compare_data(size_t a_y, size_t b_y, size_t x,
             const struct dump_table_aux *aux)
{
    return ovsdb_datum_compare_3way(&aux->data[a_y][x],
                                    &aux->data[b_y][x],
                                    &aux->columns[x]->type);
}

static int
compare_rows(size_t a_y, size_t b_y, void *aux_)
{
    struct dump_table_aux *aux = aux_;
    size_t x;

    /* Skip UUID columns on the first pass, since their values tend to be
     * random and make our results less reproducible. */
    for (x = 0; x < aux->n_columns; x++) {
        if (aux->columns[x]->type.key.type != OVSDB_TYPE_UUID) {
            int cmp = compare_data(a_y, b_y, x, aux);
            if (cmp) {
                return cmp;
            }
        }
    }

    /* Use UUID columns as tie-breakers. */
    for (x = 0; x < aux->n_columns; x++) {
        if (aux->columns[x]->type.key.type == OVSDB_TYPE_UUID) {
            int cmp = compare_data(a_y, b_y, x, aux);
            if (cmp) {
                return cmp;
            }
        }
    }

    return 0;
}

static void
swap_rows(size_t a_y, size_t b_y, void *aux_)
{
    struct dump_table_aux *aux = aux_;
    struct ovsdb_datum *tmp = aux->data[a_y];
    aux->data[a_y] = aux->data[b_y];
    aux->data[b_y] = tmp;
}

static int
compare_columns(const void *a_, const void *b_)
{
    const struct ovsdb_column *const *ap = a_;
    const struct ovsdb_column *const *bp = b_;
    const struct ovsdb_column *a = *ap;
    const struct ovsdb_column *b = *bp;

    return strcmp(a->name, b->name);
}

static void
dump_table(const char *table_name, const struct shash *cols,
           struct json_array *rows)
{
    const struct ovsdb_column **columns;
    size_t n_columns;

    struct ovsdb_datum **data;

    struct dump_table_aux aux;
    struct shash_node *node;
    struct table t;
    size_t x, y;

    /* Sort columns by name, for reproducibility. */
    columns = xmalloc(shash_count(cols) * sizeof *columns);
    n_columns = 0;
    SHASH_FOR_EACH (node, cols) {
        struct ovsdb_column *column = node->data;
        if (strcmp(column->name, "_version")) {
            columns[n_columns++] = column;
        }
    }
    qsort(columns, n_columns, sizeof *columns, compare_columns);

    /* Extract data from table. */
    data = xmalloc(rows->n * sizeof *data);
    for (y = 0; y < rows->n; y++) {
        struct shash *row;

        if (rows->elems[y]->type != JSON_OBJECT) {
            ovs_fatal(0,  "row %"PRIuSIZE" in table %s response is not a JSON object: "
                      "%s", y, table_name, json_to_string(rows->elems[y], 0));
        }
        row = json_object(rows->elems[y]);

        data[y] = xmalloc(n_columns * sizeof **data);
        for (x = 0; x < n_columns; x++) {
            const struct json *json = shash_find_data(row, columns[x]->name);
            if (!json) {
                ovs_fatal(0, "row %"PRIuSIZE" in table %s response lacks %s column",
                          y, table_name, columns[x]->name);
            }

            check_ovsdb_error(ovsdb_datum_from_json(&data[y][x],
                                                    &columns[x]->type,
                                                    json, NULL));
        }
    }

    /* Sort rows by column values, for reproducibility. */
    aux.data = data;
    aux.columns = columns;
    aux.n_columns = n_columns;
    sort(rows->n, compare_rows, swap_rows, &aux);

    /* Add column headings. */
    table_init(&t);
    table_set_caption(&t, xasprintf("%s table", table_name));
    for (x = 0; x < n_columns; x++) {
        table_add_column(&t, "%s", columns[x]->name);
    }

    /* Print rows. */
    for (y = 0; y < rows->n; y++) {
        table_add_row(&t);
        for (x = 0; x < n_columns; x++) {
            struct cell *cell = table_add_cell(&t);
            cell->json = ovsdb_datum_to_json(&data[y][x], &columns[x]->type);
            cell->type = &columns[x]->type;
            ovsdb_datum_destroy(&data[y][x], &columns[x]->type);
        }
        free(data[y]);
    }
    table_print(&t, &table_style);
    table_destroy(&t);

    free(data);
    free(columns);
}

static void
do_dump(struct jsonrpc *rpc, const char *database,
        int argc, char *argv[])
{
    struct jsonrpc_msg *request, *reply;
    struct ovsdb_schema *schema;
    struct json *transaction;

    const struct shash_node *node, **tables;
    size_t n_tables;
    struct ovsdb_table_schema *tschema;
    const struct shash *columns;
    struct shash custom_columns;

    size_t i;

    shash_init(&custom_columns);
    schema = fetch_schema(rpc, database);
    if (argc) {
        node = shash_find(&schema->tables, argv[0]);
        if (!node) {
            ovs_fatal(0, "No table \"%s\" found.", argv[0]);
        }
        tables = xmemdup(&node, sizeof(&node));
        n_tables = 1;
        tschema = tables[0]->data;
        for (i = 1; i < argc; i++) {
            node = shash_find(&tschema->columns, argv[i]);
            if (!node) {
                ovs_fatal(0, "Table \"%s\" has no column %s.", argv[0], argv[1]);
            }
            shash_add(&custom_columns, argv[1], node->data);
        }
    } else {
        tables = shash_sort(&schema->tables);
        n_tables = shash_count(&schema->tables);
    }

    /* Construct transaction to retrieve entire database. */
    transaction = json_array_create_1(json_string_create(database));
    for (i = 0; i < n_tables; i++) {
        const struct ovsdb_table_schema *ts = tables[i]->data;
        struct json *op, *jcolumns;

        if (argc > 1) {
            columns = &custom_columns;
        } else {
            columns = &ts->columns;
        }
        jcolumns = json_array_create_empty();
        SHASH_FOR_EACH (node, columns) {
            const struct ovsdb_column *column = node->data;

            if (strcmp(column->name, "_version")) {
                json_array_add(jcolumns, json_string_create(column->name));
            }
        }

        op = json_object_create();
        json_object_put_string(op, "op", "select");
        json_object_put_string(op, "table", tables[i]->name);
        json_object_put(op, "where", json_array_create_empty());
        json_object_put(op, "columns", jcolumns);
        json_array_add(transaction, op);
    }

    /* Send request, get reply. */
    request = jsonrpc_create_request("transact", transaction, NULL);
    check_txn(jsonrpc_transact_block(rpc, request, &reply), &reply);

    /* Print database contents. */
    if (reply->result->type != JSON_ARRAY
        || reply->result->u.array.n != n_tables) {
        ovs_fatal(0, "reply is not array of %"PRIuSIZE" elements: %s",
                  n_tables, json_to_string(reply->result, 0));
    }
    for (i = 0; i < n_tables; i++) {
        const struct ovsdb_table_schema *ts = tables[i]->data;
        const struct json *op_result = reply->result->u.array.elems[i];
        struct json *rows;

        if (op_result->type != JSON_OBJECT
            || !(rows = shash_find_data(json_object(op_result), "rows"))
            || rows->type != JSON_ARRAY) {
            ovs_fatal(0, "%s table reply is not an object with a \"rows\" "
                      "member array: %s",
                      ts->name, json_to_string(op_result, 0));
        }

        if (argc > 1) {
            dump_table(tables[i]->name, &custom_columns, &rows->u.array);
        } else {
            dump_table(tables[i]->name, &ts->columns, &rows->u.array);
        }
    }

    jsonrpc_msg_destroy(reply);
    shash_destroy(&custom_columns);
    free(tables);
    ovsdb_schema_destroy(schema);
}

static void
do_help(struct jsonrpc *rpc OVS_UNUSED, const char *database OVS_UNUSED,
        int argc OVS_UNUSED, char *argv[] OVS_UNUSED)
{
    usage();
}


/* "lock" command. */

struct ovsdb_client_lock_req {
    const char *method;
    char *lock;
};

static void
lock_req_init(struct ovsdb_client_lock_req *lock_req,
              const char *method, const char *lock_name)
{
    if (lock_req->method || lock_req->lock) {
        return;
    }
    lock_req->method = method;
    lock_req->lock = xstrdup(lock_name);
}

static bool
lock_req_is_set(struct ovsdb_client_lock_req *lock_req)
{
    return lock_req->method;
}

static void
lock_req_destroy(struct ovsdb_client_lock_req *lock_req)
{
    free(lock_req->lock);
    lock_req->method = NULL;
    lock_req->lock = NULL;
}

/* Create a lock class request. Caller is responsible for free
 * the 'request' message. */
static struct jsonrpc_msg *
create_lock_request(struct ovsdb_client_lock_req *lock_req)
{
    struct json *locks, *lock;

    locks = json_array_create_empty();
    lock = json_string_create(lock_req->lock);
    json_array_add(locks, lock);

    return jsonrpc_create_request(lock_req->method, locks, NULL);
}

static void
ovsdb_client_lock(struct unixctl_conn *conn, int argc OVS_UNUSED,
                  const char *argv[], void *lock_req_)
{
    struct ovsdb_client_lock_req *lock_req = lock_req_;
    lock_req_init(lock_req, "lock", argv[1]);
    unixctl_command_reply(conn, NULL);
}

static void
ovsdb_client_unlock(struct unixctl_conn *conn, int argc OVS_UNUSED,
                    const char *argv[], void *lock_req_)
{
    struct ovsdb_client_lock_req *lock_req = lock_req_;
    lock_req_init(lock_req, "unlock", argv[1]);
    unixctl_command_reply(conn, NULL);
}

static void
ovsdb_client_steal(struct unixctl_conn *conn, int argc OVS_UNUSED,
                   const char *argv[], void *lock_req_)
{
    struct ovsdb_client_lock_req *lock_req = lock_req_;
    lock_req_init(lock_req, "steal", argv[1]);
    unixctl_command_reply(conn, NULL);
}

static void
do_lock(struct jsonrpc *rpc, const char *method, const char *lock)
{
    struct ovsdb_client_lock_req lock_req = {NULL, NULL};
    struct unixctl_server *unixctl;
    struct jsonrpc_msg *request;
    struct json *request_id = NULL;
    bool exiting = false;
    bool enable_lock_request = true; /* Don't send another request before
                                        getting a reply of the previous
                                        request. */
    daemon_save_fd(STDOUT_FILENO);
    daemonize_start(false);
    lock_req_init(&lock_req, method, lock);

    if (get_detach()) {
        int error;

        error = unixctl_server_create(NULL, &unixctl);
        if (error) {
            ovs_fatal(error, "failed to create unixctl server");
        }

        unixctl_command_register("unlock", "LOCK", 1, 1,
                                  ovsdb_client_unlock, &lock_req);
        unixctl_command_register("steal", "LOCK", 1, 1,
                                  ovsdb_client_steal, &lock_req);
        unixctl_command_register("lock", "LOCK", 1, 1,
                                  ovsdb_client_lock, &lock_req);
        unixctl_command_register("exit", "", 0, 0,
                                 ovsdb_client_exit, &exiting);
    } else {
        unixctl = NULL;
    }

    for (;;) {
        struct jsonrpc_msg *msg;
        int error;

        unixctl_server_run(unixctl);
        if (enable_lock_request && lock_req_is_set(&lock_req)) {
            request = create_lock_request(&lock_req);
            request_id = json_clone(request->id);
            jsonrpc_send(rpc, request);
            lock_req_destroy(&lock_req);
        }

        error = jsonrpc_recv(rpc, &msg);
        if (error == EAGAIN) {
            goto no_msg;
        } else if (error) {
            ovs_fatal(error, "%s: receive failed", jsonrpc_get_name(rpc));
        }

        if (msg->type == JSONRPC_REQUEST && !strcmp(msg->method, "echo")) {
            jsonrpc_send(rpc, jsonrpc_create_reply(json_clone(msg->params),
                                                   msg->id));
        } else if (msg->type == JSONRPC_REPLY
                   && json_equal(msg->id, request_id)) {
            print_json(msg->result);
            putchar('\n');
            fflush(stdout);
            enable_lock_request = true;
            json_destroy(request_id);
            request_id = NULL;
            daemonize_complete();
        } else if (msg->type == JSONRPC_NOTIFY) {
            puts(msg->method);
            print_json(msg->params);
            putchar('\n');
            fflush(stdout);
        }

        jsonrpc_msg_destroy(msg);

no_msg:
        if (exiting) {
            break;
        }

        jsonrpc_run(rpc);
        jsonrpc_wait(rpc);
        jsonrpc_recv_wait(rpc);

        unixctl_server_wait(unixctl);
        poll_block();
    }

    json_destroy(request_id);
    unixctl_server_destroy(unixctl);
}

static void
do_lock_create(struct jsonrpc *rpc, const char *database OVS_UNUSED,
               int argc OVS_UNUSED, char *argv[])
{
    do_lock(rpc, "lock", argv[0]);
}

static void
do_lock_steal(struct jsonrpc *rpc, const char *database OVS_UNUSED,
              int argc OVS_UNUSED, char *argv[])
{
    do_lock(rpc, "steal", argv[0]);
}

static void
do_lock_unlock(struct jsonrpc *rpc, const char *database OVS_UNUSED,
               int argc OVS_UNUSED, char *argv[])
{
    do_lock(rpc, "unlock", argv[0]);
}

/* All command handlers (except for "help") are expected to take an optional
 * server socket name (e.g. "unix:...") as their first argument.  The socket
 * name argument must be included in max_args (but left out of min_args).  The
 * command name and socket name are not included in the arguments passed to the
 * handler: the argv[0] passed to the handler is the first argument after the
 * optional server socket name.  The connection to the server is available as
 * global variable 'rpc'. */
static const struct ovsdb_client_command all_commands[] = {
    { "list-dbs",           NEED_RPC,      0, 0,       do_list_dbs },
    { "get-schema",         NEED_DATABASE, 0, 0,       do_get_schema },
    { "get-schema-version", NEED_DATABASE, 0, 0,       do_get_schema_version },
    { "list-tables",        NEED_DATABASE, 0, 0,       do_list_tables },
    { "list-columns",       NEED_DATABASE, 0, 1,       do_list_columns },
    { "transact",           NEED_RPC,      1, 1,       do_transact },
    { "monitor",            NEED_DATABASE, 1, INT_MAX, do_monitor },
    { "monitor-cond",       NEED_DATABASE, 2, 3,       do_monitor_cond },
    { "dump",               NEED_DATABASE, 0, INT_MAX, do_dump },
    { "lock",               NEED_RPC,      1, 1,       do_lock_create },
    { "steal",              NEED_RPC,      1, 1,       do_lock_steal },
    { "unlock",             NEED_RPC,      1, 1,       do_lock_unlock },
    { "help",               NEED_NONE,     0, INT_MAX, do_help },

    { NULL,                 0,             0, 0,       NULL },
};

static const struct ovsdb_client_command *get_all_commands(void)
{
    return all_commands;
}
