/* 
 * Processor: Core i7 2630qm
 * Cores: 8
 * HT: Yes
 * Skalierungsfaktor: 4,8 
 * "rnb1kbnr/ppp2ppp/8/3qp3/8/3P4/PPP2PPP/RNBQKBNR w KQkq - 0 4"
 * 
 * 
 * 1 - s - 0,001 seconds
 * 1 - m - 					0,001 seconds
 * 2 - s - 0,012 seconds
 * 2 - m - 					0,007 seconds
 * 3 - s - 0,196 seconds
 * 3 - m - 					0,115 seconds
 * 4 - s - 1,142 seconds
 * 4 - m - 					0,513 seconds
 * 5 - s - 35,337 seconds
 * 5 - m - 					8,811 seconds
 * 6 - s - 
 * 6 - m - 					347,447 seconds 
 *  
 * 
 * Processor: Core 2 Duo T6600
 * Cores: 2
 * HT: No
 * Skalierungsfaktor: 2
 * "rnb1kbnr/ppp2ppp/8/3qp3/8/3P4/PPP2PPP/RNBQKBNR w KQkq - 0 4"
 * 
 *  3 - s - 0,398 seconds
 *  3 - m -						0,384 seconds
 *  4 - s - 2,502 seconds
 *  4 - m -						2,481 seconds
 *  5 - s - 89,373 seconds
 *  5 - m -						50,077 seconds
 *  
 *  
 *  Schon ab 2 Kernen ist bei einer Suchtiefe von 3 ein Erfolg zu sehen, weshalb das
 *  Programm ab 2 Kernen immer Multithreading verwendet.
 */






package de.htw.ds.board.chess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import de.htw.ds.board.Board;
import de.htw.ds.board.MovePrediction;
import de.htw.ds.board.Piece;
import de.sb.javase.sync.DaemonThreadFactory;

public class ChessAnalyzer2 extends ChessAnalyzer {
	
	private static final int PROCESSOR_COUNT = Runtime.getRuntime().availableProcessors();
	private static final ExecutorService executors = Executors.newCachedThreadPool(new DaemonThreadFactory());

	boolean useSingleThread = PROCESSOR_COUNT > 1?true:false;
	/**
	 * {@inheritDoc}
	 * @throws NullPointerException {@inheritDoc}
	 * @throws IllegalArgumentException {@inheritDoc}
	 */
	public MovePrediction predictMoves (final Board<ChessPieceType> board, final int depth) 
	{		
		if(useSingleThread)
		{
			return predictMovesSingleThreaded(board, depth);
		}
		
		return predictMovesMultiThreaded(board, depth);
	}


	protected final MovePrediction predictMovesMultiThreaded (final Board<ChessPieceType> board, final int depth)
	{
		if (depth <= 0) throw new IllegalArgumentException();		
		
		final boolean whiteActive = board.isWhiteActive();
		final List<MovePrediction> alternatives = new ArrayList<>();
		final Collection<short[]> moves = board.getActiveMoves();
		final Set<Future<MovePrediction>> futures = new HashSet<>();
		final Hashtable<Future<MovePrediction>, Board<ChessPieceType>> bufferedBoardClones = new Hashtable<Future<MovePrediction>, Board<ChessPieceType>>();
		final Hashtable<Future<MovePrediction>, short[]> bufferedPerformedMoves = new Hashtable<Future<MovePrediction>, short[]>();
		
		for (final short[] move : moves) 
		{

			final MovePrediction movePrediction;
			final Piece<ChessPieceType> capturePiece = board.getPiece(move[1]);
			if (capturePiece != null && capturePiece.isWhite() != whiteActive && capturePiece.getType() == ChessPieceType.KING) 
			{
				movePrediction = new MovePrediction(whiteActive ? +Board.WIN : -Board.WIN);
				movePrediction.getMoves().add(move);
			} 
			else 
			{
				final Board<ChessPieceType> boardClone = board.clone();
				boardClone.performMove(move);

				if (depth > 1) 
				{
					final PredictMovesMultiThread currentCallable = new PredictMovesMultiThread(boardClone, depth - 1);					
					final Future<MovePrediction> currentFuture = executors.submit(currentCallable);
					
					futures.add(currentFuture);	
					
					bufferedBoardClones.put(currentFuture, boardClone);					
					bufferedPerformedMoves.put(currentFuture, move);
					
					continue;
				} 
				else 
				{
					movePrediction = new MovePrediction(boardClone.getRating());
					movePrediction.getMoves().add(move);
				}
			}

			final int comparison = compareMovePredictions(whiteActive, movePrediction, alternatives.isEmpty() ? null : alternatives.get(0));
			if (comparison > 0) alternatives.clear();
			if (comparison >= 0) alternatives.add(movePrediction);
		}	
		
		for(final Future<MovePrediction> future : futures)
		{
			MovePrediction movePrediction;
			final Board<ChessPieceType> boardClone = bufferedBoardClones.get(future);
			final short[] move = bufferedPerformedMoves.get(future);
						
			try 
			{
				while (true) 
				{
					try 
					{
						movePrediction = future.get();
						break;
					} 
					catch (final InterruptedException interrupt) 
					{
						interrupt.printStackTrace();
					}
				}
			} 
			catch (final ExecutionException exception) 
			{
				final Throwable cause = exception.getCause();
				if (cause instanceof Error) throw (Error) cause;
				if (cause instanceof RuntimeException) throw (RuntimeException) cause;
				if (cause instanceof InterruptedException) throw new ThreadDeath();
				throw new AssertionError();
			}		
			
			final short[] counterMove = movePrediction.getMoves().get(0);
			if (counterMove != null) 
			{
				final Piece<ChessPieceType> recapturePiece = boardClone.getPiece(counterMove[1]);
				if (recapturePiece != null && recapturePiece.isWhite() == whiteActive && recapturePiece.getType() == ChessPieceType.KING) continue;
			}
			movePrediction.getMoves().add(0, move);
			
			final int comparison = compareMovePredictions(whiteActive, movePrediction, alternatives.isEmpty() ? null : alternatives.get(0));
			if (comparison > 0) alternatives.clear();
			if (comparison >= 0) alternatives.add(movePrediction);
		}
		
		if (alternatives.isEmpty()) 
		{ 
			final short[] activeKingPositions = board.getPositions(true, whiteActive, ChessPieceType.KING);
			final boolean kingCheckedOrMissing = activeKingPositions.length == 0 ? true : board.isPositionThreatened(activeKingPositions[0]);
			final int rating = kingCheckedOrMissing ? (whiteActive ? -Board.WIN : +Board.WIN) : Board.DRAW;

			final MovePrediction movePrediction = new MovePrediction(rating);
			for (int loop = depth; loop > 0; --loop)
				movePrediction.getMoves().add(null);
			return movePrediction;
		}
		
		return alternatives.get(ThreadLocalRandom.current().nextInt(alternatives.size()));
	}	
	
	public class PredictMovesMultiThread implements Callable<MovePrediction>
	{
		final int m_depth;
		final Board<ChessPieceType> m_board;
		
		public PredictMovesMultiThread(final Board<ChessPieceType> board, final int depth)
		{
			m_board = board;
			m_depth = depth;
		}
		
		@Override
		public MovePrediction call() throws Exception 
		{
			return ChessAnalyzer.predictMovesSingleThreaded(m_board, m_depth);
		}		
	}
}
