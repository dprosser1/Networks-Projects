import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import javax.net.ssl.*;

public class WebServer {
    private static final int HTTP_PORT  = 80;   // edit to 8080/8443 if 80/443 need admin
    private static final int HTTPS_PORT = 443;
    private static final File ROOT = new File(".").getAbsoluteFile();

    public static void main(String[] args) {
        boolean https = false;
        if (args.length == 2) {
            System.setProperty("javax.net.ssl.keyStore", args[0]);
            System.setProperty("javax.net.ssl.keyStorePassword", args[1]);
            https = true;
        } else if (args.length == 1) {
            System.err.println("Error: keystore path and password both required; running HTTP only.");
        }

        new Thread(WebServer::runHttp).start();
        if (https) new Thread(WebServer::runHttps).start();
    }

    /* ---------- listeners ---------- */
    private static void runHttp() {
        try (ServerSocket srv = new ServerSocket(HTTP_PORT)) {
            while (true) new Thread(new Handler(srv.accept())).start();
        } catch (IOException e) { e.printStackTrace(); }
    }
    private static void runHttps() {
        try {
            SSLServerSocketFactory fac = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            SSLServerSocket srv = (SSLServerSocket) fac.createServerSocket(HTTPS_PORT);
            while (true) new Thread(new Handler(srv.accept())).start();
        } catch (IOException e) { e.printStackTrace(); }
    }

    /* ---------- per‑connection ---------- */
    private static class Handler implements Runnable {
        private final Socket sock;
        Handler(Socket s) { this.sock = s; }

        public void run() {
            try (Socket s = sock;
                 InputStream in = s.getInputStream();
                 DataOutputStream out = new DataOutputStream(s.getOutputStream())) {

                ByteArrayOutputStream hb = new ByteArrayOutputStream();
                int p = 0, c;
                while ((c = in.read()) != -1) {
                    hb.write(c);
                    int len = hb.size();
                    if (p == '\r' && c == '\n' && len >= 4) {
                        byte[] b = hb.toByteArray();
                        if (b[len - 4] == '\r' && b[len - 3] == '\n') break;
                    }
                    p = c;
                }

                String[] lines = hb.toString().split("\r\n");
                if (lines.length == 0) return;
                String[] rq = lines[0].split("\\s+");
                if (rq.length < 3) { send(out,400,"Bad Request"); return; }
                String method = rq[0], target = rq[1];

                Map<String,String> hdr = new HashMap<>();
                for (int i=1;i<lines.length;i++){
                    int k = lines[i].indexOf(':');
                    if(k>0) hdr.put(lines[i].substring(0,k).trim().toLowerCase(),
                            lines[i].substring(k+1).trim());
                }

                if ("GET".equals(method))      get(target,out);
                else if ("POST".equals(method)) post(in,hdr,out);
                else                           send(out,405,"Method Not Allowed");

            } catch(IOException ignored){}
        }

        /* ---------- GET ---------- */
        private void get(String t, DataOutputStream out) throws IOException {
            String dec = URLDecoder.decode(t,"UTF-8");
            if (dec.equals("/")) dec = "/index.html";
            File f = new File(ROOT, dec.replace('/',
                    File.separatorChar)).getCanonicalFile();
            if (!f.getPath().startsWith(ROOT.getCanonicalPath())) { send(out,403,"Forbidden"); return; }
            if (!f.exists()||f.isDirectory()) { send(out,404,"Not Found"); return; }

            byte[] data = Files.readAllBytes(f.toPath());
            String mime = mime(f);
            out.writeBytes("HTTP/1.1 200 OK\r\nContent-Type: "+mime+
                    "\r\nContent-Length: "+data.length+"\r\n\r\n");
            out.write(data);
        }

        /* ---------- POST ---------- */
        private void post(InputStream in, Map<String,String> h, DataOutputStream out) throws IOException{
            String lenS = h.get("content-length");
            if(lenS==null){ send(out,400,"Bad Request"); return; }
            int len;
            try{ len=Integer.parseInt(lenS); }catch(NumberFormatException e){ send(out,400,"Bad Request"); return; }
            byte[] body = in.readNBytes(len);
            out.writeBytes("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8"+
                    "\r\nContent-Length: "+body.length+"\r\n\r\n");
            out.write(body);
        }

        /* ---------- helpers ---------- */
        private static void send(DataOutputStream out,int code,String msg)throws IOException{
            byte[] b = ("<h1>"+code+" "+msg+"</h1>").getBytes("UTF-8");
            out.writeBytes("HTTP/1.1 "+code+" "+msg+"\r\nContent-Type: text/html"+
                    "\r\nContent-Length: "+b.length+"\r\n\r\n");
            out.write(b);
        }
        private static String mime(File f)throws IOException{
            String m = Files.probeContentType(f.toPath());
            if(m!=null) return m;
            String n=f.getName().toLowerCase();
            if(n.endsWith(".html")||n.endsWith(".htm")) return "text/html";
            if(n.endsWith(".txt")||n.endsWith(".log")) return "text/plain";
            if(n.endsWith(".jpg")||n.endsWith(".jpeg")) return "image/jpeg";
            if(n.endsWith(".png")) return "image/png";
            if(n.endsWith(".gif")) return "image/gif";
            if(n.endsWith(".css")) return "text/css";
            if(n.endsWith(".js")) return "application/javascript";
            if(n.endsWith(".json")) return "application/json";
            return "application/octet-stream";
        }
    }
}
