package de.htw.ds.board.chess;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ProtocolException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.jws.WebService;
import javax.sql.DataSource;
import javax.xml.ws.Endpoint;
import javax.xml.ws.soap.SOAPBinding;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;

import de.htw.ds.board.MovePrediction;
import de.sb.javase.io.SocketAddress;
import de.sb.javase.sql.JdbcConnectionMonitor;

@WebService (endpointInterface="de.htw.ds.board.chess.ChessService", serviceName="ChessService")
public class ChessServer implements ChessService, AutoCloseable {

	private static final String PROTOCOL_IDENTIFIER = "CSP";
	static private final String SQL_SELECT_MOVE = "SELECT * FROM chess.openingmove WHERE position=? AND searchDepth>=?";
	static private final String SQL_INSERT_MOVE = "INSERT INTO chess.openingmove VALUES (0, ?, ?, ?, ?, ?)";
	private final URI serviceURI;
	private final Endpoint endpoint;
	private final Connection connection;


	private final static String url = "jdbc:mysql://localhost:3306/chess";
	private final static String user = "root";
	private final static String password = "";
	private final static String encoding = "utf-8";

	public ChessServer(final int servicePort, final String serviceName) throws SQLException
	{
		super();
		try {
			this.serviceURI = new URI("http", null, SocketAddress.getLocalAddress().getCanonicalHostName(), servicePort, "/" + serviceName, null, null);
		} catch (final URISyntaxException exception) {
			throw new IllegalArgumentException();
		}
		this.connection = ChessServer.getDataSource().getConnection();
		connection.setAutoCommit(false);
		// NEW RUNNABLE CONNECTION MONITOR
		final Runnable connectionMonitor = new JdbcConnectionMonitor(this.connection, "select null", 60000);
		final Thread thread = new Thread(connectionMonitor, "jdbc-connection-monitor");
		thread.setDaemon(true);
		thread.start();
		this.endpoint = Endpoint.create(SOAPBinding.SOAP11HTTP_BINDING, this);
		this.endpoint.publish(this.serviceURI.toASCIIString());
	}


	/**
	 * Returns the parameter needed for logging into the DB
	 * @return
	 * @throws SQLException
	 */
	public static DataSource getDataSource() throws SQLException 
	{ 
		final MysqlDataSource dataSource = new MysqlDataSource(); 
		dataSource.setURL(url);
		dataSource.setUser(user);
		dataSource.setPassword(password);
		dataSource.setCharacterEncoding(encoding);
		return dataSource; 
	}

	@Override
	public MovePrediction[] getMovePredictions(String xfen, short searchDepth)
			throws SQLException 
		{
		final ArrayList<MovePrediction> moves = new ArrayList<MovePrediction>();
		// TODO Auto-generated method stub
		synchronized(this.connection)
		{
			try
			{
				try (PreparedStatement statement = this.connection.prepareStatement(SQL_SELECT_MOVE)) 
				{
					statement.setString(1, splitXfen(xfen));
					statement.setByte(2, (byte) searchDepth);
					System.out.println(statement);
					try (ResultSet resultSet = statement.executeQuery())
					{
//						MovePrediction prediction;
						if (!resultSet.next()) 
						{
							System.out.println("Server didn't found results!!! Return null");
							return null;
						}
						else 
						{
							do 
							{
								MovePrediction move = new MovePrediction(resultSet.getInt("rating"));
								short[] values = new short[2];
								values[0] = (short)resultSet.getInt("source");
								values[1] = (short)resultSet.getInt("sink");
								System.out.println("Server found values: (0)="+values[0]+" (1)="+values[1]);
								move.getMoves().add(values);
								moves.add(move);
							}
							while (resultSet.next());
						}
					}
				}
			}
			catch (final Exception exception)
			{
				try
				{
					this.connection.rollback();
				}
				catch (final Exception nestedException)
				{
					nestedException.addSuppressed(nestedException);
				}
				throw exception;
			}
			this.connection.commit();
		}
		MovePrediction[] movesArray = new MovePrediction[moves.size()];
		movesArray = moves.toArray(movesArray);
		return movesArray;
	}


	@Override
	public void putMovePrediction(String xfen, short searchDepth, MovePrediction movePrediction) /*throws SQLException*/
	{
		synchronized(this.connection) 
		{
			try 
			{
				PreparedStatement statement = this.connection.prepareStatement(SQL_INSERT_MOVE);
				statement.setString(1, splitXfen(xfen));
				statement.setByte(2, (byte) movePrediction.getMoves().get(0)[0]);
				statement.setByte(3, (byte) movePrediction.getMoves().get(0)[1]);
				int rating = movePrediction.getRating();
				System.out.println("Rating before DB: " + rating);
				statement.setByte(4, (byte) rating);
				statement.setByte(5, (byte) searchDepth);

//				if (statement.executeUpdate() != 1) 
//				{
//					throw new IllegalStateException("insert failed.");
//				}
				this.connection.commit();
			}	 
			catch (final Exception exception) 
			{
				try 
				{
					this.connection.rollback();
				} 
				catch (final Exception nestedException) 
				{
					exception.addSuppressed(nestedException);
				}
//				throw exception;
			}

			// TODO Auto-generated method stub
		}
	}

	/**
	 * Method to split the X-FEN String
	 * @param xfen
	 * @return
	 */
	private String splitXfen(String xfen)
	{
		String splitedXfen = xfen.split(" ")[0]+" "+xfen.split(" ")[1]+" "+xfen.split(" ")[2];
		return splitedXfen;
	}
	
	/**
	 * Returns the service URI.
	 * @return the service URI
	 */
	public URI getServiceURI () {
		return this.serviceURI;
	}
	
	@Override
	public void close() throws SQLException {
		try {
			endpoint.stop();
		} catch (final Throwable exception) {}
		this.connection.close();
	}

	
	
	static public void main (final String[] args) throws URISyntaxException, SQLException, IOException {
		
		final long timestamp 		= System.currentTimeMillis();
		final int servicePort 		= args.length < 1 ? 3308 : Integer.parseInt(args[0]);
		final String serviceName 	= args.length < 2 ? "ChessService" : args[1];
		

		try (ChessServer server = new ChessServer(servicePort, serviceName)) {
			System.out.println("Dynamic (bottom-up) JAX-WS shop server running.");
			System.out.format("Service URI is \"%s\".\n", server.getServiceURI());
			System.out.format("Startup time is %sms.\n", System.currentTimeMillis() - timestamp);
			if(args.length>2)
			{
				final int controlPort = Integer.parseInt(args[2]);
				final String passwort = args[3];
				System.out.println("Custom-Protokoll runs on port: "+controlPort+" with password: "+passwort);
				if(passwort == null)
					throw new IllegalArgumentException();
			
					final ServerSocket serviceSocket = new ServerSocket(controlPort);
					while(true)
					{
						Socket socket = null;
						try{
							socket = serviceSocket.accept();
							//HorchBlock
							final BufferedOutputStream bufferedByteSink = new BufferedOutputStream(socket.getOutputStream());
							final DataOutputStream dataSink = new DataOutputStream(bufferedByteSink);
							final DataInputStream dataSource = new DataInputStream(socket.getInputStream());
				
							// verify and acknowledge protocol
							for (final char character : PROTOCOL_IDENTIFIER.toCharArray()) {
								if (dataSource.readChar() != character) throw new ProtocolException();
							}
							dataSink.writeChars(PROTOCOL_IDENTIFIER);
							bufferedByteSink.flush();
							
							final String method = dataSource.readUTF();
							if(method.equals(passwort))
							{
								dataSink.writeUTF("ok");
								bufferedByteSink.flush();
								break;						
							}
							else
							{
								dataSink.writeUTF("fail");
								bufferedByteSink.flush();
							}
						} catch (final Exception exception) {
							try {
								exception.printStackTrace();
							} catch (final Exception nestedException) {}
						} finally {
							try {
								socket.close();
							} catch (final Exception exception) {}
							try{
								serviceSocket.close();
							} catch (final Exception exception) {}
						}
					}
			}
			else
			{
				System.out.println("enter \"quit\" to stop.");
				final BufferedReader charSource = new BufferedReader(new InputStreamReader(System.in));
				while (!"quit".equals(charSource.readLine()));
			}
		}
	}
	

}
