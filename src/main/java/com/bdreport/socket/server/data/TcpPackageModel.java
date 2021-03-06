package com.bdreport.socket.server.data;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSON;

import io.netty.channel.ChannelHandlerContext;

@Component
public class TcpPackageModel {
	private byte[] bytesMsg;

	private String ipAddr = "";
	private int inetPort = 0;

	private static Logger logger = Logger.getLogger(TcpPackageModel.class.getName());

	private JmsRealDataModel dataModel = new JmsRealDataModel();

	public static final int PACKAGE_PARSE_SUCCEED = 0x00;
	public static final int PACKAGE_PARSE_FAILED_PACKAGE_NULL = 0x10;
	public static final int PACKAGE_PARSE_FAILED_PACKAGE_EMPTY = 0x20;
	public static final int PACKAGE_PARSE_FAILED_FUNCCODE_UNKOWN = 0x25;
	public static final int PACKAGE_PARSE_FAILED_PACKAGE_BROKEN = 0x30;
	public static final int PACKAGE_PARSE_FAILED_DATA_BROKEN = 0x40;
	public static final int PACKAGE_PARSE_FAILED_DATA_CHECKSUM_ERROR = 0x50;

	public static final byte PACKAGE_FRAME_HEAD_BYTE_EE = (byte) 0xEE;
	public static final byte PACKAGE_FRAME_TAIL_BYTE_FF = (byte) 0xFF;
	public static final byte PACKAGE_FRAME_TAIL_BYTE_FC = (byte) 0xFC;
	
	public static final int PACKAGE_FRAME_HEAD_STATUS_NULL = 0;
	public static final int PACKAGE_FRAME_HEAD_STATUS_START = 1;
	public static final int PACKAGE_FRAME_TAIL_STATUS_NULL = 0;
	public static final int PACKAGE_FRAME_TAIL_STATUS_START = 1;
	public static final int PACKAGE_FRAME_TAIL_STATUS_2 = 2;
	public static final int PACKAGE_FRAME_TAIL_STATUS_3 = 3;
	public static final int PACKAGE_FRAME_TAIL_STATUS_END = 4;

	public TcpPackageModel() {

	}

	public TcpPackageModel(byte[] buf) {
		from(buf);
	}

	public String getIpAddr() {
		return ipAddr;
	}

	public void setIpAddr(String ipAddr) {
		this.ipAddr = ipAddr;
	}

	public int getInetPort() {
		return inetPort;
	}

	public void setInetPort(int inetPort) {
		this.inetPort = inetPort;
	}

	public TcpPackageModel(ChannelHandlerContext ctx, byte[] buf) {
		InetSocketAddress socket = ((InetSocketAddress) (ctx.channel().remoteAddress()));
		ipAddr = socket.getAddress().getHostAddress();
		inetPort = socket.getPort();
		from(buf);
	}

	public JmsRealDataModel getDataModel() {
		return dataModel;
	}

	public void setDataModel(JmsRealDataModel dataModel) {
		this.dataModel = dataModel;
	}

	public void fromBytes(byte[] buf) {
		from(buf);
	}

	public String toHexString() {
		return Hex.encodeHexString(bytesMsg).toUpperCase();
	}

	public String toJsonString() {
		return JSON.toJSONString(dataModel);
	}

	public byte checkSum(byte[] buf) {
		byte sum = 0;
		for (int i = 0; i < buf.length; i++) {
			sum = (byte) (sum + buf[i]);
		}
		byte[] hex = new byte[1];
		hex[0] = sum;
		logger.debug("Data: " + Hex.encodeHexString(buf).toUpperCase());
		logger.debug("Data Checksum: " + Hex.encodeHexString(hex).toUpperCase());
		return sum;
	}

	private int from(byte[] buf) {
		if (buf == null) {// package null
			logger.debug("Package Null Error.");
			return PACKAGE_PARSE_FAILED_PACKAGE_NULL;
		}
		int len = buf.length;
		if (len < 2) {// package empty
			logger.debug("Package Empty Error.");
			return PACKAGE_PARSE_FAILED_PACKAGE_EMPTY;
		}
		bytesMsg = Arrays.copyOf(buf, len);
		byte funcCode = (byte) 0;

		funcCode = bytesMsg[1];
		if (funcCode == (byte) 0xB1 || funcCode == (byte) 0xB2 || funcCode == (byte) 0xB3 || funcCode == (byte) 0xB4) {
			if (len < 13) {// package broken
				logger.debug("Package Broken Error.");
				return PACKAGE_PARSE_FAILED_PACKAGE_BROKEN;
			}
			int gatewayNo = (int) (((bytesMsg[2] & 0xFF) << 8) | (bytesMsg[3] & 0xFF));
			int year = (int) (((bytesMsg[4] & 0xFF) << 8) | (bytesMsg[5] & 0xFF));
			int month = (int) bytesMsg[6];
			int day = (int) bytesMsg[7];
			int hour = (int) bytesMsg[8];
			int minute = (int) bytesMsg[9];
			int second = (int) bytesMsg[10];
			int length = (int) (((bytesMsg[11] & 0xFF) << 8) | (bytesMsg[12] & 0xFF));
			if (len < 13 + length) {// data broken
				logger.debug("Package Data Broken Error, Data Length: " + length);
				return PACKAGE_PARSE_FAILED_DATA_BROKEN;
			}
			byte[] data = Arrays.copyOfRange(bytesMsg, 13, 13 + length);
			int datalen = length / 2;
			List<Float> dataList = new ArrayList();

			if (bytesMsg[13 + length] == checkSum(data)) {
				for (int i = 0; i < datalen; i++) {
					float f = toFloat((short) (((data[i * 2] & 0xFF) << 8) | (data[i * 2 + 1] & 0xFF)));
					dataList.add(f);
				}
			} else { // Data Checksum Error
				logger.debug("Package Data Checksum Error.");
				return PACKAGE_PARSE_FAILED_DATA_CHECKSUM_ERROR;
			}
			String strTime = String.format("%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, minute, second);
			logger.debug("Package Data Time: " + strTime);
			byte[] fc = new byte[1];
			fc[0] = funcCode;
			dataModel.initDataModel(ipAddr, inetPort, Hex.encodeHexString(fc).toUpperCase(), gatewayNo, strTime,
					datalen, dataList);
		} else {
			logger.debug("Package FuncCode Unkown: " + funcCode);
			return PACKAGE_PARSE_FAILED_FUNCCODE_UNKOWN;
		}
		logger.debug("Package Parse Succeed.");
		return PACKAGE_PARSE_SUCCEED;
	}

	public static float toFloat(final short half) {
		switch ((int) half) {
		case 0x0000:
			return 0.0f;
		case 0x8000:
			return -0.0f;
		case 0x7c00:
			return Float.POSITIVE_INFINITY;
		case 0xfc00:
			return Float.NEGATIVE_INFINITY;
		default:
			return Float.intBitsToFloat(
					((half & 0x8000) << 16) | (((half & 0x7c00) + 0x1C000) << 13) | ((half & 0x03FF) << 13));
		}
	}

	public static short toHalfFloat(final float v) {
		if (Float.isNaN(v))
			throw new UnsupportedOperationException("NaN to half conversion not supported!");
		if (v == Float.POSITIVE_INFINITY)
			return (short) 0x7c00;
		if (v == Float.NEGATIVE_INFINITY)
			return (short) 0xfc00;
		if (v == 0.0f)
			return (short) 0x0000;
		if (v == -0.0f)
			return (short) 0x8000;
		if (v > 65504.0f)
			return 0x7bff; // max value supported by half float
		if (v < -65504.0f)
			return (short) (0x7bff | 0x8000);
		if (v > 0.0f && v < 5.96046E-8f)
			return 0x0001;
		if (v < 0.0f && v > -5.96046E-8f)
			return (short) 0x8001;

		final int f = Float.floatToIntBits(v);

		return (short) (((f >> 16) & 0x8000) | ((((f & 0x7f800000) - 0x38000000) >> 13) & 0x7c00)
				| ((f >> 13) & 0x03ff));
	}

	public static byte[] shortToByteArray(short s) {
		byte[] targets = new byte[2];
		for (int i = 0; i < 2; i++) {
			int offset = (targets.length - 1 - i) * 8;
			targets[i] = (byte) ((s >>> offset) & 0xff);
		}
		return targets;
	}
}
