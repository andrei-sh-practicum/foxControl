# R8 / ProGuard rules for Fox Control
#
# Hilt and Room ship their own consumer rules with the libraries; nothing to add here.

# Jakarta Mail (Angus): SMTP/IMAP/POP3 providers, charset/address maps and the activation
# framework are looked up by class name from META-INF/javamail.* and mailcap files at runtime.
# Without these rules R8 renames/removes them and sending fails with NoSuchProviderException
# in release builds.
-keep class org.eclipse.angus.mail.** { *; }
-keep class org.eclipse.angus.activation.** { *; }
-keep class jakarta.mail.** { *; }
-keep class jakarta.activation.** { *; }
-dontwarn org.eclipse.angus.**
-dontwarn jakarta.mail.**
-dontwarn jakarta.activation.**
