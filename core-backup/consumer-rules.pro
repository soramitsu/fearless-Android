# google-http-client still ships its deprecated Apache transport. The backup engine uses
# NetHttpTransport and the Apache HttpClient artifacts are excluded, so those references stay unresolved.
-dontwarn org.apache.http.**
-dontwarn com.google.api.client.http.apache.**
