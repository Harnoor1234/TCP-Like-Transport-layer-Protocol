package sender;

import java.io.*;
import java.util.ArrayList;
import java.lang.Math;
import static java.lang.System.out;
import java.net.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.*;

public class Sender implements Runnable{

        /* Class Variables */
private static byte[] file_bytes;                                              
private static DatagramSocket socket;                                            
private static BufferedWriter log_writer;                                             
private static  Queue<DatagramPacket> awaiting_ack = new LinkedList<DatagramPacket>(); // hold packets for potential re-send
private static long timer = 0;                                                        
private static long timeout = 500; // dynamically changing timeout time (ms)
private static int remote_port, ack_port, window_size;
private static String filename, log_filename;
private static byte[] bFile;
private static long estimated_RTT = 0;
private static long dev_RTT = 0;
private static Hashtable<Integer, Long> times = new Hashtable<Integer, Long>();       // departure times for packets (ms)
private static int packet_number = 0;                                                 // sequence number
private static int packets_needed;
private static int next_ack = 0;
       

private static InetAddress remote_ip;

                                                

        
        /* Main */
public static void main(String[] args) throws Exception {
check(args);
loadFile(filename);
packets_needed = file_bytes.length/(256 - 20) + 1; //The data excluding 20 bytes of the TCP header//
socket = new DatagramSocket(ack_port);
log_writer = startLog(log_filename);
send();
        }
        
        /*
         * Check the command line arguments for proper form, setting class variables if they do.
         */
        private static void check(String[] args) {
        
        if (args.length != 6){
        System.err.println("\nImproper command format, please try again.");
        System.err.println("java Sender [filename] [remote_IP] [remote_port] "+ "[ack_port_number] [log_filename] [window_size]\n");
        System.exit(1);
                }
                
        else if (Integer.parseInt(args[5])<=0 || Integer.parseInt(args[5]) >= 65535){
        System.err.println("\nImproper window size. Please choose an integer value greater than 0.");
        System.exit(1);
        }
        else {
        filename = args[0];
        try {
        remote_ip = InetAddress.getByName(args[1]);
        remote_port = Integer.parseInt(args[2]);
        ack_port = Integer.parseInt(args[3]);
                                
            log_filename = args[4];
            window_size = Integer.parseInt(args[5]);
                        } 
            catch (Exception e) {
            System.err.println("\nError parsing arguments.\n");
            e.printStackTrace();
            System.exit(1);
            }
                }
        }
        
      
private static void loadFile(String filename){ // Convert the file into byte for transmission
File file = new File(filename);
file_bytes = new byte[(int)file.length()];
try {
FileInputStream stream = new FileInputStream(file);
stream.read(file_bytes);
stream.close();} 
catch (Exception e) {
e.printStackTrace();
System.err.println("\nError converting file into bytes.\n");
System.exit(1);
                }
        }

        /*
         * Manages the sending of data packets and receptions of ACKs.
         */
private static void send() {
try {
// start a thread to send packets
new Thread(new Sender()).start();
// listen for acks
while (true) {
byte[] buffer = new byte[256];
// receive ACK packet
DatagramPacket ack_packet = new DatagramPacket(buffer, buffer.length);
socket.receive(ack_packet);
// log it
logAckPacket(ack_packet);
// restart timer
timer = System.currentTimeMillis();

// shift window if seq_num matches
int ack_seq_num = toInteger(Arrays.copyOfRange(buffer, 4, 8));
if (ack_seq_num == next_ack){
awaiting_ack.poll();
next_ack ++;
}



// if an ACK with fin_flag true is received, we're done     
byte[] flags = Arrays.copyOfRange(buffer, 13, 14);
boolean fin_flag = (Boolean) (flags[0] == (byte) 1); 
if (fin_flag) {
System.out.println("\nDelivery completed successfully.\n");
log_writer.close();
System.exit(1);
}
}
} catch (Exception e) {
                  e.printStackTrace();
                }
                
        }

        /*
         * Write a log entry to the previously determined logfile, adding RTT estimate.
         */
        private static void logAckPacket(DatagramPacket ack_packet) throws UnknownHostException {
                byte[] buffer = ack_packet.getData();
                // extract the header fields
                int seq_num = toInteger(Arrays.copyOfRange(buffer, 4, 8));
                int ack_num = toInteger(Arrays.copyOfRange(buffer, 8, 12));        
                byte[] flags = Arrays.copyOfRange(buffer, 13, 14);
                boolean fin_flag = (Boolean) (flags[0] == (byte) 1); 
                String entry = "Time(ms): " + System.currentTimeMillis() + " ";
                entry += "Source: " + remote_ip.getHostAddress() + ":" + remote_port + " ";
                entry += "Destination: " + InetAddress.getLocalHost().getHostAddress() + ": " + ack_port + " ";
                entry += "Sequence #: " + seq_num + " ";
                entry += "ACK #: " + ack_num + " ";
                entry += "FIN: " + fin_flag + " ";
                // RTT
                long RTT = -1;
                if (times.containsKey(seq_num))
                        RTT = System.currentTimeMillis() - times.get(seq_num);
                        estimated_RTT = (long) (0.875*estimated_RTT + 0.125*estimated_RTT);
                        dev_RTT = (long)(0.75*dev_RTT + 0.25*Math.abs(RTT-estimated_RTT));
                        timeout = estimated_RTT+4*dev_RTT;
                if (RTT >= 0){
                        entry += "RTT(ms): " + RTT + "\n";
                } 
                else {
                        entry += "RTT(ms): NA" + "\n";
                }
                try {
                        log_writer.write(entry);
                        log_writer.flush();
                } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println("\nError encountered writing to logfile.\n");
                }
        }

        /*
         * Write a log entry to the previously determined logfile, just the packet.
         */
        private static void logSentPacket(DatagramPacket packet) throws UnknownHostException{
                byte[] buffer = packet.getData();
                // extract the header fields
                int seq_num = toInteger(Arrays.copyOfRange(buffer, 4, 8));
                int ack_num = toInteger(Arrays.copyOfRange(buffer, 8, 12));
                byte[] flags = Arrays.copyOfRange(buffer, 13, 14);
                boolean fin_flag = (Boolean) (flags[0] == (byte) 1); 
                String entry = "Time(ms): " + System.currentTimeMillis() + " ";
                entry += "Source: " + InetAddress.getLocalHost().getHostAddress() + ":" + ack_port + " ";
                entry += "Destination: " + remote_ip.getHostAddress() + ":" + remote_port + " ";
                entry += "Sequence #: " + seq_num + " ";
                entry += "ACK #: " + ack_num + " ";
                entry += "FIN: " + fin_flag + "\n";
                try {
                        log_writer.write(entry);
                        log_writer.flush();
                } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println("\nError encountered writing to logfile.\n");
                }
        }
        
        /*
         * Independent thread to handle sending of data packets.
         */
        public void run(){
                try {
                        while (true) {
                                if (canSendMore()) {
                                        // make a new packet, send it, start the RTT timer, add to queue, log it
        DatagramPacket packet = makePacket();
        socket.send(packet);
        times.put(packet_number, System.currentTimeMillis());
        packet_number++;
        awaiting_ack.add(packet);
        logSentPacket(packet);
         }
                                else if(timeout()){
                                        // reset the timer, double timeout, and send packets again
                                        System.out.println("\t\t TIMEOUT at " + timeout);
                                        timer = System.currentTimeMillis();
                                        timeout = timeout*2;
                                        sendAgain();
                                }
                        }
                } catch (Exception e) {
                        e.printStackTrace();
                        // print error
                }
        }
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
         * Constructs the next appropriate data packet for delivery.
         */
        private static DatagramPacket makePacket() {
                DatagramPacket packet = null;
                // grab relevant file_bytes
                int start_index = packet_number*236;
                int end_index = (packet_number+1)*236;
                byte [] data = Arrays.copyOfRange(file_bytes, start_index, end_index);
                byte [] all;
                try {
                        // calculate checksum
                MessageDigest digest = MessageDigest.getInstance("MD5");
                digest.update(data);
                byte[] digest_bytes = digest.digest();
                byte[] checksum = Arrays.copyOfRange(digest_bytes, 0, 2);
                        
                        // check if this is the last byte, set the flag
                int fin_flag = 0;
                if (packet_number == packets_needed - 1)
                fin_flag = 1;

                        // append the header to the data
                all = concat(intToTwo(ack_port),concat(intToTwo(remote_port),concat(intToFour(packet_number),concat(intToFour(next_ack),
                concat(intToTwo(fin_flag),
                concat(intToTwo(1),
                concat(checksum,
                concat(intToTwo(0), data))))))));
                        
                        // construct packet
                packet = new DatagramPacket(all, all.length, remote_ip, remote_port);
                } catch (Exception e){
                e.printStackTrace();
                System.err.println("\nError constructing packet.\n");
                }
                return packet;
        }
        
        
        private boolean timeout() {
        if (System.currentTimeMillis()-timer > timeout)
        return true;else 
        return false;
        }
        
        /*
         * Sends all the packets in the queue again.
         */
        private void sendAgain() {
        Object[] packets = awaiting_ack.toArray();
        for (Object object : packets){
        try {
                                DatagramPacket packet = (DatagramPacket) object;
                                logSentPacket(packet);
                                socket.send(packet);
                        } catch (Exception e) {
                                e.printStackTrace();
                                System.err.println("\nError sending packets from queue.\n");
                        }
                }
        }
        
        /*
         * Check to see if sending more packets is allowed.
         */
        private boolean canSendMore() {
                // is the window full
                if (awaiting_ack.size() == window_size) {
                return false;
                } 
                // has the last packet been sent
                if (packet_number >= packets_needed) {
                return false;
                }
                else return true;
        }
        public static int toInteger(byte[] bytes) {
                // pad byte[2] to byte[4]
                if (bytes.length != 4) {
                bytes = concat(new byte[2], bytes);
                }
                return ByteBuffer.wrap(bytes).getInt();
        }
        public static byte[] intToTwo(int number) {
                byte[] bytes = new byte[2];
                bytes[0] = (byte) (number >>> 8);
                bytes[1] = (byte) number;
                return bytes;
        }

        /* converts into 4 bytes*/
        public static byte[] intToFour(int number) {
                byte[] bytes = new byte[4];
                bytes[0] = (byte) (number >>> 24);
                bytes[1] = (byte) (number >>> 16);
                bytes[2] = (byte) (number >>> 8);
                bytes[3] = (byte) number;
                return bytes;
        }
        public static BufferedWriter startLog(String log_filename) {
                // write to standard out
                if (log_filename.equals("stdout")) {
                        return new BufferedWriter(new OutputStreamWriter(System.out));
                }
                // write to a specific file
                else {
                        try {
                                File file = new File(log_filename);
                                if (!file.exists()) {
                                file.createNewFile();
                                }
                                return new BufferedWriter(new FileWriter(file.getAbsoluteFile(), true));
                        } catch (Exception e) {
                                e.printStackTrace();
                                System.err.println("\nError encountered creating logfile\n");
                                System.exit(1);
                        }
                }
                return null;
        }
}

