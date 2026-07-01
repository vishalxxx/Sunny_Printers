/**
 * Supabase PostgREST API layer (HTTP). Use {@link api.supabase.SupabaseClients}
 * to build a {@link api.supabase.SupabaseRestClient} from stored
 * {@link model.SupabaseSettings}, then call table-specific flows from services
 * or migrate each process off local SQLite. To probe all table endpoints from the CLI,
 * run {@link api.supabase.SupabaseTablesHealthCheck#main(String[])} with the app classpath.
 */
package api.supabase;
