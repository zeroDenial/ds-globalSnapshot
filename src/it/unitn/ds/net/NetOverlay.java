package it.unitn.ds.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Provides the network overlay layer for the distributed banking system
 * 
 * @author Daniel Zozin
 */
public interface NetOverlay {

	/**
	 * Start the local server by using the given branchId and load all the other
	 * branches addresses. The calling thread will be blocked until the
	 * operation completes.
	 * 
	 * @param localBranch
	 *            the local branchId
	 * @param branches
	 *            A map that associates branches with their network addresses
	 * @throws IOException
	 *             if network initialization failed
	 * @throws InterruptedException
	 */
	void start(int localBranch, Map<Integer, InetSocketAddress> branches) throws IOException, InterruptedException;

	/**
	 * Send the given message to the specified remote branch. The message will
	 * be queued and sent the returned future is notified once the message is
	 * successfully delivered
	 * 
	 * @return The future used to indicate the completion of the delivery
	 */
	Future<Message> sendMessage(int remoteBranch, Message m);

	/**
	 * @return The next incoming message, if any. Null if there is no message.
	 */
	Message receiveMessage();

	/**
	 * Generic overlay message
	 */
	abstract class Message {

		int seqn;
		int senderId;
		int destId;

		int getSenderId() {
			return senderId;
		}

		@Override
		public String toString() {
			return "Message [seqn=" + seqn + ", senderId=" + senderId + "]";
		}
	}

	/**
	 * Money transfer message
	 */
	class Transfer extends Message {

		private final long amount;

		public Transfer(long amount) {
			this.amount = amount;
		}

		public long getAmount() {
			return amount;
		}

		@Override
		public String toString() {
			return "Transfer [amount=" + amount + ", seqn=" + seqn + ", senderId=" + senderId + "]";
		}
	}
}
