package de.htw.ds.board.chess;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Random;

import javax.xml.ws.Service;

import de.htw.ds.board.Board;
import de.htw.ds.board.MovePrediction;
import de.sb.javase.io.SocketAddress;
import de.sb.javase.xml.Namespaces;

public class ChessAnalyzer3 extends ChessAnalyzer {

	private static Random random = new Random();
	static URI SERVICE_URI;
	ChessService serviceProxy = null;
	
	static
	{
		try
		{
			SERVICE_URI = new URI("http", null, SocketAddress.getLocalAddress().getCanonicalHostName(), 3308, "/" + "ChessService", null, null); 
		}
		catch (final URISyntaxException uri)
		{
			uri.printStackTrace();
		}
	}

	public ChessAnalyzer3()
	{
		
	}

	public MovePrediction predictMoves (final Board<ChessPieceType> board, final int depth) 
	{
		MovePrediction movepredictionForDB = new MovePrediction(0);
		if(board.getMoveClock() >= 20)
		{
			movepredictionForDB = ChessAnalyzer.predictMovesSingleThreaded(board, depth);
			return movepredictionForDB;
		}
		else
		{
			try {
				if(serviceProxy == null)
				{
					URL wsdlLocator = new URL(SERVICE_URI + "?wsdl");
					Service proxyFactory = Service.create(wsdlLocator, Namespaces.toQualifiedName(ChessService.class));
					serviceProxy = proxyFactory.getPort(ChessService.class);
				}
				MovePrediction[] moveprediction = serviceProxy.getMovePredictions(board.toString(), (short) depth);

				
				
				if (moveprediction != null && moveprediction.length > 0)
				{
					int r = random.nextInt(moveprediction.length);
					movepredictionForDB =  moveprediction[r];
				}
				else
				{
					movepredictionForDB = ChessAnalyzer.predictMovesSingleThreaded(board, depth);
					serviceProxy.putMovePrediction(board.toString(), (short) depth, movepredictionForDB);
				}
			} catch (Exception e) {
				e.printStackTrace();
				return ChessAnalyzer.predictMovesSingleThreaded(board, depth);
			}
		}
		return movepredictionForDB; 
	}
}

