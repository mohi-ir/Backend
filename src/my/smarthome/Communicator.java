/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package my.smarthome;

import gnu.io.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.TooManyListenersException;
import javax.swing.*;
import java.awt.Color;
import java.util.List;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Xarrin
 */
public class Communicator implements SerialPortEventListener{
    
    //passed from main GUI
    private SmartHomeUI window;
    private JComboBox cbox;
    private JTextArea logField;
    private JTextArea readData;
    private JTextField nodeIdField;
    private JTextField serverIdField;
    private JTextField netIdField;
   
    public Communicator(SmartHomeUI win){
        this.window = win;
        this.cbox = window.getCombo();
        this.logField = window.getLogField();
        this.readData = window.getReadData();
        this.nodeIdField = window.getNodeId();
        this.serverIdField = window.getServerId();
        this.netIdField = window.getNetId();
    }
    
    private Enumeration ports = null;
    private HashMap portMap = new HashMap();

    //this is the object that contains the opened port
    private CommPortIdentifier selectedPortIdentifier = null;
    private SerialPort serialPort = null;

    //input and output streams for sending and receiving data
    private InputStream input = null;
    private OutputStream output = null;
    
    private boolean bConnected = false;

    //the timeout value for connecting with the port
    final static int TIMEOUT = 2000;

    //some ascii values for for certain things
    final static int SPACE_ASCII = 32;
    final static int DASH_ASCII = 45;
    final static int NEW_LINE_ASCII = 10;
    String logText = "";
    
    static String delimiter ="\r";
    //static String ackBegin = "ack:";
   // static String ack ="";
        
    // application parameters
    final static String MESSAGE_CONFIG = "+Config";
    final static String MESSAGE_TEST_PACKET = "+TestPacket";
    
    //List<String> ackBuffer = new ArrayList<String>();
    List<Integer> ackBuffer = new ArrayList<Integer>();
    //Map<Integer, String> ackBuffer = new HashMap<Integer, String>();
    //int index =0;
    
    private BufferedReader inputBuffer;
       
     
    //search for all the serial ports
    public void searchForPorts()
    {
        ports = CommPortIdentifier.getPortIdentifiers();

        while (ports.hasMoreElements())
        {
            CommPortIdentifier curPort = (CommPortIdentifier)ports.nextElement();

            //get only serial ports
            if (curPort.getPortType() == CommPortIdentifier.PORT_SERIAL)
            {
                boolean flag=true;
                for(int i=0;i<cbox.getItemCount();i++)
                {
                    if(cbox.getItemAt(i)== curPort.getName())
                    {
                        flag = false;
                        break;
                    }
                }
                if(flag){
                cbox.addItem(curPort.getName());
                portMap.put(curPort.getName(), curPort);
                }
            }
        }
    }
    
    //set the connection : true
    public void setConnected(boolean con){
        bConnected = con;
    }
    
    //get the state of connection
    public boolean getConnected(){
        return bConnected;
    }
    
    //connect to the selected port in the combo box
    public void connect()
    {
        String selectedPort = (String)cbox.getSelectedItem();
        selectedPortIdentifier = (CommPortIdentifier)portMap.get(selectedPort);

        CommPort commPort = null;

        try
        {
            commPort = selectedPortIdentifier.open("TigerControlPanel", TIMEOUT);
            serialPort = (SerialPort)commPort;

            setConnected(true);

            logText = selectedPort + " opened successfully.";
            logField.append(logText + "\n");

            serialPort.setSerialPortParams(115200,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);

        }
        catch (PortInUseException e)
        {
            logText = selectedPort + " is in use. (" + e.toString() + ")";
            //logField.setForeground(Color.RED);
            logField.append(logText + "\n");
        }
        catch (Exception e)
        {
            logText = "Failed to open " + selectedPort + "(" + e.toString() + ")";
            logField.append(logText + "\n");
        }
    }
    
    //open the input and output streams
    public boolean initIOStream()
    {
        //return value for whether opening the streams is successful or not
        boolean successful = false;

        try {
           
            inputBuffer = new BufferedReader(new InputStreamReader(serialPort.getInputStream()));
            output = serialPort.getOutputStream();
           
            successful = true;
            return successful;
        }
        catch (IOException e) {
            logText = "I/O Streams failed to open. (" + e.toString() + ")";
            logField.append(logText + "\n");
            return successful;
        }
    }
    
    //starts the event listener that knows whenever data is available to be read
    public void initListener()
    {
        try
        {
            serialPort.addEventListener(this);
            serialPort.notifyOnDataAvailable(true);
        }
        catch (TooManyListenersException e)
        {
            logText = "Too many listeners. (" + e.toString() + ")";
            logField.append(logText + "\n");
        }
    }
    
    //disconnect the serial port
    public void disconnect()
    {
        try
        {
            serialPort.removeEventListener();
            serialPort.close();
            input.close();
            output.close();
            setConnected(false);
            logText = "Disconnected.";
            logField.append(logText + "\n");
        }
        catch (Exception e)
        {
            logText = "Failed to close " + serialPort.getName()
                              + "(" + e.toString() + ")";
            logField.setForeground(Color.red);
            logField.append(logText + "\n");
        }
    }
    
    //Reading Data from serialPort
    public void serialEvent(SerialPortEvent evt) {
        if (evt.getEventType() == SerialPortEvent.DATA_AVAILABLE){
            try {
                while (inputBuffer.ready ()){
                  String inputLine=inputBuffer.readLine();
                 
                  String [] parsedPacket = parsePacket(inputLine);
                  
                  //if packet belonges to the same serverId and packet belongs to this node
                  if(parsedPacket.length ==6 ){
                    if (parsedPacket[1].equals(serverIdField.getText()) && parsedPacket[2].equals(nodeIdField.getText())){
                         readData.append(inputLine +"\n");

                     if(parsedPacket[0].equals("Test")){

                          String ack = "ack:"+parsedPacket[1]+","+parsedPacket[3]+","+parsedPacket[2]+","+parsedPacket[4] + "," + parsedPacket[5] + "\r";
                          writeData(ack);
                          }

                      else{

                         ackBuffer.add(Integer.parseInt(parsedPacket[4]));
                      }
                  }
              }
            }
        } catch (Exception e) {
             System.err.println(e.toString());
            }
                   
        }
    }
    
    public String [] parsePacket(String s){
        String [] result = new String [6];
        String [] splitMessageId = s.split(":");
        
        
        
        if(splitMessageId.length ==2){
            
        //messageId
        result[0] = splitMessageId[0];
        
        String [] splitWords = splitMessageId[1].split(",");
        
        if(splitWords.length ==5){
        
          //serverId
            result[1] = splitWords[0];

            //destId
            result[2] = splitWords[1];

            //sourceId
            result[3] = splitWords[2];

            //packetNumber
            result[4] = splitWords[3];

            //packetText
            result[5] = splitWords[4];
        }
        
        }
        
      
        return result;
    }
        
    public void writeData(String s){
         try{
                output.write(s.getBytes());
                output.flush();
               logText = "message sent : "+ s;
               logField.append(logText + "\n");
                                             

            }catch (Exception e){
                logText = "Failed to write data. (" + e.toString() + ")";
                logField.append(logText + "\n");
            }
    }
    
    public void test1(String message){
        
        for(int i=0;i<100;i++){
            String s = message;
            s += i;
            s += ",";
            
            for(int k=s.length();k<32;k++){
                s += "-";
            }
            
            s += ",";
            s += "\r";
            //s += "\n" ;
            
        writeDataWaiteForAck(s,i);
    }
        
     if(!ackBuffer.isEmpty()){
      if(ackBuffer.contains(0)){
          logField.append( "ack: " + 0  + "\n");
      }
    }
}
    
    //write data to serialPort
    public void writeDataWaiteForAck(String packet,int ack)
    {
           /* final Thread thread = new Thread(){
          
            public void run(){

                    long beginTime = System.currentTimeMillis(); 
                    writeData(packet);
                                              
                                     
                    while(true){
                        if(ackBuffer.contains(ack)){
                            long endTime = System.currentTimeMillis();
                            long dt = endTime - beginTime;
                            logField.append( "ack: " + ack + " , recieved in " + dt + "ms" + "\n");
                            ackBuffer.remove(ack);
                            break;
                        }
                    }
                              
    }
        };
       thread.start();
       //thread.interrupt();             */
        
        
                    long beginTime = System.currentTimeMillis(); 
                  
                    writeData(packet);                          
                                     
                    while(true){
                        long endTime = System.currentTimeMillis();
                        long dt = endTime - beginTime;
                        
                        if(!ackBuffer.isEmpty()){
                            if(ackBuffer.contains(ack)){

                                logField.append( "ack: " + ack + " , recieved in " + dt + "ms" + "\n");
                                //ackBuffer.remove(ack);
                                break;
                            }
                        }
                        if(dt > 100){
                            //logField.append( "Time-out 10 seconds" + "\n");
                            break;
                        }
                    }
                
   }
    
}
