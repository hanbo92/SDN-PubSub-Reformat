package edu.bupt.wangfu.mgr.topology;

import edu.bupt.wangfu.info.device.Flow;
import edu.bupt.wangfu.info.device.Switch;
import edu.bupt.wangfu.info.msg.udp.MsgHello;
import edu.bupt.wangfu.info.msg.udp.Route;
import edu.bupt.wangfu.mgr.base.SysInfo;
import edu.bupt.wangfu.mgr.message.HelloReceiver;
import edu.bupt.wangfu.mgr.message.ReHelloReceiver;
import edu.bupt.wangfu.opendaylight.MultiHandler;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * Created by lenovo on 2016-6-22.
 */
//只有localCtl == groupCtl时，才启动这个
public class HeartMgr extends SysInfo {
	public HeartMgr() {
		downRcvhelloRehelloFlow();

		new Thread(new HelloReceiver()).start();
		new Thread(new ReHelloReceiver()).start();

		new Thread(new HelloTask()).start();

		Properties props = new Properties();
		String propertiesPath = "DtConfig.properties";
		try {
			props.load(new FileInputStream(propertiesPath));
		} catch (IOException e) {
			e.printStackTrace();
		}
		reHelloPeriod = Long.parseLong(props.getProperty("reHelloPeriod"));//判断失效阀值
		helloPeriod = Long.parseLong(props.getProperty("helloPeriod"));//发送周期
	}

	private void downRcvhelloRehelloFlow() {
		for (Switch swt : outSwitchs) {
			for (String out : swt.portSet) {
				if (!out.equals("LOCAL")) {
					//这条路径保证outPort进来hello消息可以传回groupCtl
					List<String> hello = null;
					//这条路径保证从groupCtl发出来的re_hello都能到达borderSwt
					List<String> re_hello = null;
					for (Route r : groupRoutes) {
						if (r.startSwtId.equals(localSwtId) && r.endSwtId.equals(swt.id)) {
							re_hello = r.route;
						} else if (r.startSwtId.equals(swt.id) && r.endSwtId.equals(localSwtId)) {
							hello = r.route;
						}
					}
					if (re_hello == null) {
						re_hello = RouteMgr.calRoute(localSwtId, swt.id);
					}
					if (hello == null) {
						hello = RouteMgr.calRoute(swt.id, localSwtId);
					}
					//这里流表的out设置为portWsn2Swt，是因为只有在groupCtl == localCtl时才调用这个函数
					RouteMgr.downRouteFlows(re_hello, portWsn2Swt, out, "re_hello", "sys", groupCtl);
					RouteMgr.downRouteFlows(hello, out, portWsn2Swt, "hello", "sys", groupCtl);
				}
			}
		}
	}

	private void sendHello(String out, String swtId) {
		MsgHello hello = new MsgHello();
		MultiHandler handler = new MultiHandler(uPort, "hello", "sys");

		hello.startGroup = groupName;
		hello.startOutPort = out;
		hello.startBorderSwtId = swtId;
		hello.reHelloPeriod = reHelloPeriod;

		handler.v6Send(hello);
	}

	//依次向每个outPort发送Hello信息
	private class HelloTask implements Runnable {
		@Override
		public void run() {
			for (Switch swt : outSwitchs) {
				for (String out : swt.portSet) {
					if (!out.equals("LOCAL")) {
						List<String> ctl2out = null;
						List<String> out2ctl = null;
						for (Route r : groupRoutes) {
							if (r.startSwtId.equals(localSwtId) && r.endSwtId.equals(swt.id)) {
								ctl2out = r.route;
							} else if (r.startSwtId.equals(swt.id) && r.endSwtId.equals(localSwtId)) {
								out2ctl = r.route;
							}
						}
						if (ctl2out == null) {
							ctl2out = RouteMgr.calRoute(localSwtId, swt.id);
						}
						if (out2ctl == null) {
							out2ctl = RouteMgr.calRoute(swt.id, localSwtId);
						}
						List<Flow> c2o = RouteMgr.downRouteFlows(ctl2out, portWsn2Swt, out, "hello", "sys", groupCtl);
						List<Flow> o2c = RouteMgr.downRouteFlows(out2ctl, out, portWsn2Swt, "re_hello", "sys", groupCtl);

						sendHello(out, swt.id);
						//发送后阻塞线程，这期间：对面收到hello，回复re_hello，最后再发送一条最终版的hello
						//这之后（无论之前是否回复），都继续发下一条
						try {
							Thread.sleep(helloPeriod);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						//删除这次握手的流表，准备下次的
						RouteMgr.delRouteFlows(c2o);
						RouteMgr.delRouteFlows(o2c);
					}
				}
			}
		}
	}

}
