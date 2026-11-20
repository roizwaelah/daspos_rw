# Project-specific R8 rules.

# Room keeps generated implementations via compile-time references, but keeping
# database model names makes stack traces and schema-related diagnostics clearer.
-keep class com.daspos.db.** { *; }

# Supabase/remote payloads are built with org.json and direct field access in app
# code, so no reflective DTO keeps are required at this time.

# Optional desktop/annotation classes referenced by libraries but not packaged in
# Android runtime.
-dontwarn java.awt.Color
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
