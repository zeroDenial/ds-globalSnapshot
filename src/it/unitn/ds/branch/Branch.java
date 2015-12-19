package it.unitn.ds.branch;

import it.unitn.ds.net.NetOverlay;
import it.unitn.ds.net.NetOverlay.Message;
import it.unitn.ds.net.NetOverlay.Token;
import it.unitn.ds.net.NetOverlay.Transfer;
import it.unitn.ds.net.UDPNetOverlay;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class Branch {

	// Maximum amount of money for a single transfer
	private static final int MAX_TRANSFER = 100;

	// Transmission rate for generation random money transfers (in ms)
	private static final int TRANSFER_RATE = 200;

	private final ScheduledExecutorService BRANCH_THREADS = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {

		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "Branch Thread");
			t.setDaemon(true);
			return t;
		}
	});

	private final int localId;
	private final Map<Integer, InetSocketAddress> branches;

	private final NetOverlay overlay;

	private final SnapshotHelper snapshot;

	private List<Integer> randBranches;
	private int nextBranch = 0;
	private Random rand = new Random();

	// Money available (not reserved) that can be used for transfers
	private long availableAmounts;

	// Money reserved for pending outgoing transfers
	private long reservedAmounts = 0;

	public Branch(int localId, Map<Integer, InetSocketAddress> branches, long initialBalance) {
		this.localId = localId;
		this.branches = branches;

		overlay = new UDPNetOverlay();
		snapshot = new SnapshotHelper(branches.size(), localId);

		this.availableAmounts = initialBalance;

		randBranches = new ArrayList<Integer>(branches.keySet());
		randBranches.remove(localId);
		Collections.shuffle(randBranches);
	}

	public void start() throws IOException, InterruptedException {
		overlay.start(localId, branches);

		// Triggers message processing once the first message arrives
		overlay.receiveMessage().thenAcceptAsync((m) -> processMessage(m), BRANCH_THREADS);

		// Schedule random sending of money transfers with a fixed rate
		BRANCH_THREADS.scheduleWithFixedDelay(() -> sendRandomTransfer(), 0, TRANSFER_RATE, TimeUnit.MILLISECONDS);
	}

	private void processMessage(Message m) {

		if (m instanceof Transfer)
			processTransfer((Transfer) m);
		else
			processToken((Token) m);

		// Reschedule for receiving the next message
		overlay.receiveMessage().thenAcceptAsync((msg) -> processMessage(msg), BRANCH_THREADS);
	}

	private void processTransfer(Transfer m) {
		// If snapshot is active the transfer may be counted
		snapshot.newTransferReceived(m.getSenderId(), m.getAmount());

		// Increase balance
		availableAmounts += m.getAmount();
		// TODO Auto-generated method stub

	}

	private void processToken(Token m) {
		boolean isNewSnapshot = snapshot.newTokenReceived(m.getSenderId(), m.getSnapshotId(), availableAmounts);

		if (!isNewSnapshot)
			return;

		// Broadcasts tokens to all branches except itself
		try {
			broadcastTokens(m.getSnapshotId());
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
	}

	// Transfer random money to random branch
	private void sendRandomTransfer() {
		if (availableAmounts == 0)
			return;

		int maxValue = (int) Math.min(MAX_TRANSFER, availableAmounts);
		long amount = (maxValue > 0) ? 1 + (rand.nextInt(maxValue)) : 1;

		// Move the transferred amount from available to reserved
		availableAmounts -= amount;
		reservedAmounts += amount;

		// Once the transfer is completed the reserved amounts is reduced (by
		// the same thread executor)
		overlay.sendMessage(getRandomBranch(), new Transfer(amount)).thenAcceptAsync((t) -> {
			reservedAmounts -= t.getAmount();
		}, BRANCH_THREADS);
	}

	// Randomly choose a destination branch
	private int getRandomBranch() {
		if (this.nextBranch == randBranches.size()) {
			Collections.shuffle(randBranches);
			this.nextBranch = 0;
		}
		return randBranches.get(this.nextBranch++);

	}

	public boolean startSnapshot(int snapshotId) {
		if (snapshot.isActive()) {
			System.out.println("Skipped global snapshot " + snapshotId + " from branch " + localId + " (snapshot " + snapshot.getActiveId() + " still in progress)");
			return false;
		}

		System.out.println("Starting global snapshot " + snapshotId + " from branch " + localId);
		BRANCH_THREADS.execute(() -> {
			snapshot.startSnapshot(snapshotId, availableAmounts);

			// Send tokens to all other branches, no transfer occurs between
			// saving the availableAmounts and broadcasting the tokens
			try {
				broadcastTokens(snapshotId);
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		});
		return true;
	}

	private void broadcastTokens(long snapshotId) throws InterruptedException, ExecutionException {
		Set<Integer> destBranches = new HashSet<Integer>(branches.keySet());

		destBranches.remove(localId);

		for (int branch : destBranches) {
			overlay.sendMessage(branch, new Token(snapshotId));
		}
	}

	/**
	 * @return The total branch balance: available amounts + amounts reserved
	 *         for transfers
	 */
	public long getTotalBalance() {
		return availableAmounts + reservedAmounts;
	}

	/**
	 * @return The balance available to the branch
	 */
	public long getAvailableBalance() {
		return availableAmounts;
	}

	public void stop() {
		BRANCH_THREADS.shutdown();
	}
}
