    package com.insys.sewingmachine;
    /*
    import com.pi4j.Pi4J;
    import com.pi4j.context.Context;
    import com.pi4j.io.i2c.I2C;
    import com.pi4j.io.i2c.I2CConfig;
    import com.pi4j.io.i2c.I2CProvider;
    import com.pi4j.io.i2c.I2CRegisterDataReader;
    */

    import java.sql.Connection;
    import java.sql.DatabaseMetaData;
    import java.sql.DriverManager;
    import java.sql.PreparedStatement;
    import java.sql.SQLException;
    import java.sql.Timestamp;
    import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

    /*
     * To enable i2c communication: 
     * $ sudo raspi-config
     * 
     * If bus still not accessible at runtime: possibly no permission for current user. Check with: 
     * $ ls -la /dev/i2c* 
     * 
     * Solve with:
     * $ groupadd i2c
     * $ chown :i2c /dev/i2c-1
     * $ chmod g+rw /dev/i2c-1
     * $ usermod -aG i2c username
     * as root after reboot # echo 'KERNEL=="i2c-[0-9]*", GROUP="i2c"' >> /etc/udev/rules.d/10-local_i2c_group.rules
     */
    
    /*
     * An output of around 1270 corresponds to a RMS-measurement of 0.088V at the sensor which corresponds to 0.44 Amps.
     * P = U x I x cos(phi) = √3 x 425 V x 0,44 A x 0,9 = 345Watt at 1270 leading to a factor of 3.68.
     * 
     * To measure an AC-signal without a rectifier, a sampling frequency twice the signal-frequency should be used.
     * The signal-frequency is 1/50=20ms => max sampling period = 10ms.
     * To deal with the changing polarity of the AC-signal, the measurements are added to large vector and the average
     * of the absolute values is taken.
     * 
     * For DC-signals, the above can be ignored.
     */

    public class ADSTest {
    	
    	static int sampleLength = 64;
		static float scalar = 3.68f;
		static int samplingPeriod = 10; // In ms
		static int queryPeriod = 0; // In ms. If 0, no query will be done
		static int printPeriod = 500; // In ms. If 0, no printing will be done
		/*
        static Context pi4j = Pi4J.newAutoContext();
		static I2CProvider i2CProvider = pi4j.provider("linuxfs-i2c");
		static I2CConfig i2cConfig = I2C.newConfigBuilder(pi4j).id("ADS1015").bus(1).device(0x48).build();
		static I2C device=  i2CProvider.create(i2cConfig);
		*/
		
		static Connection connection = null;
		static byte[] data;
    	static int measurement;
		static int[] samples = new int[sampleLength];
    	static int printCounterMax;
    	static int queryCounterMax;
		
    	public ADSTest(){
    	}
    	
    	public static int averageOfArray(int[] array) {
    		int cummulative=0;
    		for (int i=0; i<array.length; i++) {
    			cummulative = cummulative + Math.abs(array[i]); //of hier absolute value
    		}
    		return cummulative/array.length;
    	}
    	
    	public static void addToArray(int[] array, int toAdd) {
    		for (int i=1; i<array.length; i++) {
    			array[i-1] = array[i];
    		}
    		array[array.length-1] = toAdd;
    	}

    	
    	public static void initialize() {
    		queryCounterMax = (int) (queryPeriod / samplingPeriod);
    		printCounterMax = (int) (printPeriod / samplingPeriod);
    		String connectionUrl =
                    "jdbc:sqlserver://10.179.1.30:1433;"
                            + "database=SEWMAN;"
                            + "user=sa;"
                            + "password=sql;"
                            + "encrypt=false;"
                            + "trustServerCertificate=false;"
                            + "loginTimeout=30;";
    		/*
            try {
            	connection = DriverManager.getConnection(connectionUrl);
                DatabaseMetaData dm = (DatabaseMetaData) connection.getMetaData();
                System.out.println("Driver name: " + dm.getDriverName());
                System.out.println("Driver version: " + dm.getDriverVersion());
                System.out.println("Product name: " + dm.getDatabaseProductName());
                System.out.println("Product version: " + dm.getDatabaseProductVersion());
            }
            catch (SQLException e) {
                e.printStackTrace();
            }
            
            try {
            	byte[] config2 = {(byte)0x84, (byte)0x83};
    			// Select configuration register
    			// AINP = AIN0 and AINN = AIN1, +/- 2.048V, Continuous conversion mode, 128 SPS
    			device.writeRegister(0x01, config2, 0, 2);
    			Thread.sleep(2000);
    			// Read 2 bytes of data
    			// raw_adc msb, raw_adc lsb
    			data = new byte[2];
            } catch (InterruptedException e) {
				e.printStackTrace();
			}
	
			 */
            System.out.println("Initialisation complete");
    	}
    	
    	public static void readData() {
    		int printCounter = 0; // is used to print out the result every x ticks
    		int queryCounter = 0; // is used to query the result every x ticks
    		int result;
			while(true) {
				printCounter++;
				queryCounter++;
				/*
				device.readRegister(0x00, data, 0, 2);
				int raw_adc = ((data[0] & 0xFF) * 256) + (data[1] & 0xFF);
				if (raw_adc > 32767){
					raw_adc -= 65535;
				}
				
				measurement = (int) (raw_adc / scalar); //measured signal in Watts
				*/
				
				Integer measurement = ThreadLocalRandom.current().nextInt(10);
				addToArray(samples, measurement);
				
				if (printPeriod != 0) {
					if(printCounter == printCounterMax){
						result = averageOfArray(samples);
						System.out.println("The sample-array: " + Arrays.toString(samples) + "\n");
						System.out.println("Average of array: " + result + "\n");
						printCounter = 0;
					}	
				}
				
				//SQL-query
				if (queryPeriod != 0) {
					if(queryCounter == queryCounterMax) {
						String query = "INSERT INTO test_current VALUES(?, ?)";
						
						try {
							PreparedStatement insert = connection.prepareStatement(query);
							insert.setInt(1, averageOfArray(samples));
							insert.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
							insert.executeUpdate();
						} catch (SQLException ex) {
			                ex.printStackTrace();
						} 
						queryCounter = 0;
					}
				}
				
				if (printCounter == 1000000) { //keep counter from running out of bounds
					printCounter = 0;
				}				
				if (queryCounter == 1000000) {
					queryCounter = 0;
				}
				
				try {
					Thread.sleep(samplingPeriod); //sampling period in ms
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
    	}
    	
    	public static int readDataGraph() {
    		for (int i=0; i<64; i++) {
    			Integer measurement = ThreadLocalRandom.current().nextInt(10);
				addToArray(samples, measurement);
    		}
			try {
				Thread.sleep(samplingPeriod); //sampling period in ms
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    		return averageOfArray(samples);
    	}
    	
    	public static void main(String[] args) throws Exception {
    		initialize();
    		readData();
    	}
    }