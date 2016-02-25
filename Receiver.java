package receiver;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.security.MessageDigest;

public class Receiver {
        
        /* Class variables */
        private static String filename;
        private static int listening_port;
        private static InetAddress remote_ip;
        private static int remote_port;
        private static String log_filename;

        /* main */
        public static void main(String[] args) throws Exception {
                check(args);
                
                BufferedWriter log_writer = startLog(log_filename);
                byte[] bytes_received = receive(log_writer);
                writeToFile(bytes_received, filename);        
        }

        /*
         * Check the command line arguments for proper form.
         */
       
        private static void check(String[] args) throws UnknownHostException {
                // check length
                if (args.length != 5)
                        screwyou();
                // check types
                else {
                        try {
                                int port = Integer.parseInt(args[1]);
                                port = Integer.parseInt(args[3]);
                                InetAddress ip = InetAddress.getByName(args[2]);
                        } catch (Exception e) {
                                // let them know what they've done
                                e.printStackTrace();
                                screwyou();
                        }
                }
                // set class variables;
                filename = args[0];
                listening_port = Integer.parseInt(args[1]);
                remote_ip = InetAddress.getByName(args[2]);
                remote_port = Integer.parseInt(args[3]);
                log_filename = args[4];
        }

        /*
         * Instruct the user how to properly execute the program.
         */
        private static void screwyou() {
                System.err.println("\nImproper command format, please try again.");
                System.err.println("java Receiver [filename] [listening_port] "
                                + "[remote_ip] [remote_port] [log_filename]\n");
                System.exit(1);
        }

        /*
         * Open a BufferedWriter to the provided log_filename.
         */
        public static BufferedWriter startLog(String log_filename) {
                // write to standard out
        if (log_filename.equals("stdout")) {
        return new BufferedWriter(new OutputStreamWriter(System.out));}
                // write to a specific file
else {
    try {
         File file = new File(log_filename);
         if (!file.exists()) {
         file.createNewFile();
                                }
         return new BufferedWriter(new FileWriter(file.getAbsoluteFile(), true));} 
         catch (Exception e) {
         e.printStackTrace();
         System.err.println("\nError encountered creating logfile\n");
         System.exit(1);
                        }
                }
        return null;
        }

        /*
         * Receive data sent via UDP and construct the logfile.
         */
        private static byte[] receive(BufferedWriter log_writer) throws Exception {
        byte[] bytes_received = null;
        DatagramSocket socket = new DatagramSocket(listening_port);
        int expected_seq_num = 0;
        boolean fin_flag = false;
                // loop until the fin_flag is received
        while (!fin_flag) {
                        // receive a packet of data up to size 256 Bytes
        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        
        socket.receive(packet);
                        // separate header from data
            
        byte[] Header = Arrays.copyOfRange(buffer, 0, 20);
        byte[] data = Arrays.copyOfRange(buffer, 20, buffer.length);
                        // extract the header fields
        int source_port = toInteger(Arrays.copyOfRange(Header,0, 2));
        int dest_port = toInteger(Arrays.copyOfRange(Header, 2, 4));
        int seq_num = toInteger(Arrays.copyOfRange(Header, 4, 8));
        int ack_num = toInteger(Arrays.copyOfRange(Header, 8, 12));
        byte[] flags = Arrays.copyOfRange(Header, 13, 14);
        fin_flag = (Boolean) (flags[0] == (byte) 1); // only flag that matters
        byte[] rec_window = Arrays.copyOfRange(Header, 14, 16);
        byte[] checksum = Arrays.copyOfRange(Header, 16, 18);
        byte[] urgent = Arrays.copyOfRange(Header, 18, 20);
                        // write log entry for received packet
        log(remote_ip.getHostAddress(), remote_port, 
        InetAddress.getLocalHost().getHostAddress(), listening_port, 
        seq_num, ack_num, fin_flag, log_writer);
                        // validate the correctness of the received packet
    if (validate(seq_num, expected_seq_num, checksum, data)) {
                                // add data to received
    if (bytes_received == null) {
    bytes_received = data;
    } else {
    bytes_received = concat(bytes_received,
    data);
    }
                                // change expected seq number and send ack
    expected_seq_num++;
    byte[] ack = concat(intToTwo(dest_port),concat(intToTwo(source_port),concat(intToFour(seq_num),concat(intToFour(expected_seq_num),concat(Arrays.copyOfRange(buffer, 12, 13),concat(flags,concat(rec_window,concat(checksum,urgent))))))));
    DatagramPacket ackPacket = new DatagramPacket(ack, ack.length, remote_ip, remote_port);
    socket.send(ackPacket);
                                // write log entry
    log(InetAddress.getLocalHost().getHostAddress(), listening_port, remote_ip.getHostAddress(), remote_port, seq_num, expected_seq_num, fin_flag, log_writer);
                                // ack is only a header, so increment the seq num
}
}
                // all packets received
    return bytes_received;
}

        /*
         * Validates that the received packet is the expected one.
         */
        public static boolean validate(int actual, int expected, byte[] checksum, byte[] data) {
                // compare actual and expected seq_num
        if (actual != expected) {
        return false;
                }
                // perform checksum
        try {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        digest.update(data);
        byte[] to_compare = Arrays.copyOfRange(digest.digest(), 0, 2);
        if (checksum[0] == to_compare[0] && checksum[1] == to_compare[1]) {
        return true;
        } else {
        return false;
        }
        } 
        catch (Exception e) {
        e.printStackTrace();
        System.err.println("\nChecksum error encountered\n");
                }
                return false;
        }

        
        
        public static int toInteger(byte[] bytes) {
               
                if (bytes.length != 4) {
                bytes = concat(new byte[2], bytes);
                }
                return ByteBuffer.wrap(bytes).getInt();
        }

        /*
         * Concatenate two byte arrays.
         */
        public static byte[] concat(byte[] first, byte[] second) {
                byte[] to_return = new byte[first.length + second.length];
                for (int i = 0; i < first.length; i++) {
                to_return[i] = first[i];
                }
                for (int j = 0; j < second.length; j++) {
                to_return[first.length + j] = second[j];
                }
                return to_return;
        }

        /*
         * Writes a log entry to the designated logfile.
         */
        private static void log(String source_ip, int source_port,
                                String dest_ip, int dest_port, 
                                int seq_num, int ack_num, boolean fin, 
                                BufferedWriter log_writer) {
                String entry = "Time(ms): " + System.currentTimeMillis() + " ";
                entry += "Source: " + source_ip + ":" + source_port + " ";
                entry += "Destination: " + dest_ip + ":" + dest_port + " ";
                entry += "Sequence #: " + seq_num + " ";
                entry += "ACK #: " + ack_num + " ";
                entry += "FIN: " + fin + "\n";
                try {
                        log_writer.write(entry);
                        log_writer.flush();
                } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println("\nError encountered writing to logfile.\n");
                }
        }

        /*
         * Reconstruct the original file and save it to the provided filename.
         */
        private static void writeToFile(byte[] bytes_received, String filename) {
                bytes_received = cleanBytes(bytes_received);
                try{
                        FileOutputStream stream = new FileOutputStream(filename);
                        stream.write(bytes_received);
                        stream.close();
                        System.out.println("\n " + filename + " has been successfully received!!");
                } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println("\nError encountered creating output file.\n");
                }
                       return;
        }

        /*
         * Remove the useless (byte)0's from the end of the bytes_received array.
         */
        private static byte[] cleanBytes(byte[] bytes_received) {
                int end_index = -1;
                for (int i = bytes_received.length - 1; i >=0; i--){
                        if (bytes_received[i] == (byte)0){
                                end_index = i;
                        }
                        else break;
                }
                if (end_index > 0)
                        return Arrays.copyOfRange(bytes_received, 0, end_index);
                else return bytes_received;
        }

        /*
         * Converts an integer to 16 bits (2 bytes), useful for Source and Dest
         * port #'s.
         */
        public static byte[] intToTwo(int number) {
                byte[] bytes = new byte[2];
                bytes[0] = (byte) (number >>> 8);
                bytes[1] = (byte) number;
                return bytes;
        }

        /*
         * Converts an integer to 32 bits (4 bytes), useful for sequence and
         * acknowledgement numbers.
         */
        public static byte[] intToFour(int number) {
                byte[] bytes = new byte[4];
                bytes[0] = (byte) (number >>> 24);
                bytes[1] = (byte) (number >>> 16);
                bytes[2] = (byte) (number >>> 8);
                bytes[3] = (byte) number;
                return bytes;
        }
}
