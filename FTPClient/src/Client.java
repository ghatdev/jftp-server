import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Client {


    private Socket socket;
    private String serverIP;
    private int serverPort;
    private InputStream inputStream;
    private OutputStream outputStream;

    public Client(String serverIP, int serverPort) {
        this.serverIP = serverIP;
        this.serverPort = serverPort;

        try {
            socket = new Socket(this.serverIP, this.serverPort);
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
        }
        catch (UnknownHostException unknownHostException) {
            System.err.println("Unknown host or connect to host failed.");
            System.exit(-1);
        }
        catch (IOException ioException) {
            System.err.println("Failed to connect host.");
            System.exit(-2);
        }

    }

    public Client() {
        this("127.0.0.1",2020);
    }

    public Client(String serverIP) {
        this(serverIP, 2020);
    }

    /**
     * FTP command implementation: LIST
     * list files from server.
     * @param path server path to list file
     * @return list String
     */
    public void cmdList(String path) {
        byte[] cmd = makeCommand("LIST", path.getBytes());
        send(cmd);

        byte[] received;
        try {
            received = read();
        }catch (JobFailedException e) {
            System.err.printf("%s: %s\n", "Failed", e.getError());
            return;
        }

        System.out.print(new String(received));
    }

    public void quitConn() {
        byte[] cmd = makeCommand("QUIT", "".getBytes());
        send(cmd);
    }

    public void cmdPut(String file) {
        File f = new File(file);
        if (!f.exists()) {
            System.out.println("File not found!");
            return;
        }

        FileInputStream fileInputStream = null;

        try {
            fileInputStream = new FileInputStream(f);

        } catch (FileNotFoundException e) {
            // WTF?
        }

        byte[] out = new byte[(int)f.length()];
        try {
            fileInputStream.read(out);
        } catch (IOException e) {
            e.printStackTrace();
        }

        ByteBuffer bf = ByteBuffer.allocate(255 + (int)f.length());
        bf.put(Arrays.copyOf(f.getName().getBytes(), 255));
        bf.put(out);

        byte[] cmd = makeCommand("PUT", bf.array());

        send(cmd);

        System.out.printf("%s transferred / %d bytes\n", f.getName(), f.length());
    }

    public void cmdGet(String file) {
        send(makeCommand("GET", file.getBytes()));

        byte[] received;
        try {
            received = read();
        } catch (JobFailedException e) {
            System.err.printf("%s: %s\n", "Failed", e.getError());
            return;
        }

        System.out.printf("Received %s / %d bytes\n", file, received.length);

        FileOutputStream fileOutputStream;
        File f = new File(file);
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        try {
            fileOutputStream = new FileOutputStream(f);
        } catch (FileNotFoundException e) {
            // WTF?
            return;
        }

        try {
            fileOutputStream.write(Arrays.copyOfRange(received,255, received.length));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void cmdCd(String path) {
        send(makeCommand("CD", path.getBytes()));
        byte[] result;
        try {
            result = read();
        } catch (JobFailedException e) {
            System.err.printf("%s: %s\n", "Failed", e.getError());
            return;
        }

        System.out.println(new String(result));
    }

    private int send(byte[] buffer) {
        try {
            outputStream.write(buffer);
            outputStream.flush();
        } catch (IOException e) {
            return -1;
        }

        return buffer.length;
    }

    private byte[] read() throws JobFailedException {
        byte[] header = new byte[12];
        try {
            inputStream.read(header, 0, 12);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String type = new String(Arrays.copyOfRange(header, 0, 4)).trim();
        String statusCode = new String(Arrays.copyOfRange(header, 4, 8)).trim();

        int dataLen = Integer.parseInt(new String(Arrays.copyOfRange(header, 8, 12)).trim());

        byte[] buffer = new byte[dataLen];

        try {
            inputStream.read(buffer, 0, dataLen);
        } catch (IOException e){
            e.printStackTrace();
        }

        if (type.equals("ERR") && !statusCode.equals("200")){
            throw new JobFailedException(new String(buffer));
        }

        return buffer;
    }

    private byte[] makeCommand(String command, byte[] arg) {
        ByteBuffer bf = ByteBuffer.allocate(8 + arg.length);

        byte[] cmd = Arrays.copyOf(command.getBytes(), 4);
        bf.put(cmd);

        byte[] argSize =  Arrays.copyOf(Integer.toString(arg.length).getBytes(),4);
        bf.put(argSize);

        bf.put(arg);


        return bf.array();
    }

    /**
     * Wrapper function to read input stream to byte array.
     * @param dst destination buffer
     * @return read size. -1 means error.
     */
    private int readToBuf(byte[] dst) {
        int result;

        try {
            result = inputStream.read(dst);

        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }

        return result;
    }

    private class JobFailedException extends Exception {
        private String error;
        public JobFailedException(String error) {
            this.error = error;
        }

        public String getError() {
            return this.error;
        }
    }
}
