package it.unitn.ds.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import it.unitn.ds.net.LinkAckEncoder.MessageAck;

/**
 * Encode ack messages
 */
@Sharable
public class LinkAckEncoder extends MessageToByteEncoder<MessageAck> {

	public static final byte LNK_ACK = 0x2;

	@Override
	protected void encode(ChannelHandlerContext ctx, MessageAck msg, ByteBuf out) throws Exception {
		// Link layer header
		out.writeByte(LNK_ACK);
		out.writeInt(msg.seqn);
		out.writeInt(msg.senderId);
	}

	static class MessageAck {

		int seqn;
		int senderId;

		public MessageAck(int seqn, int senderId) {
			this.seqn = seqn;
			this.senderId = senderId;
		}

		@Override
		public String toString() {
			return "MessageAck [seqn=" + seqn + ", senderId=" + senderId + "]";
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			MessageAck other = (MessageAck) obj;
			if (senderId != other.senderId)
				return false;
			if (seqn != other.seqn)
				return false;
			return true;
		}
	}
}
