/*
 * Copyright 2013-2015 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.legacy.app.FragmentStatePagerAdapter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager.widget.ViewPager;

import org.bitcoinj.core.Peer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainServiceImpl;
import de.schildbach.wallet.service.BlockchainState;
import de.schildbach.wallet.util.ViewPagerTabs;
import subgeneius.dobbs.wallet.R;

/**
 * @author Andreas Schildbach
 */
public final class NetworkMonitorActivity extends AbstractWalletActivity
{
	private PeerListFragment peerListFragment;
	private BlockListFragment blockListFragment;
	private DebugFragment debugFragment;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.network_monitor_content);

		final ViewPager pager = (ViewPager) findViewById(R.id.network_monitor_pager);

		final FragmentManager fm = getFragmentManager();

		if (pager != null)
		{
			final ViewPagerTabs pagerTabs = (ViewPagerTabs) findViewById(R.id.network_monitor_pager_tabs);
			pagerTabs.addTabLabels(R.string.network_monitor_peer_list_title,
					R.string.network_monitor_block_list_title,
					R.string.network_monitor_debug_title);

			final PagerAdapter pagerAdapter = new PagerAdapter(fm);

			pager.setAdapter(pagerAdapter);
			pager.setOnPageChangeListener(pagerTabs);
			pager.setPageMargin(2);
			pager.setPageMarginDrawable(R.color.bg_less_bright);

			peerListFragment = new PeerListFragment();
			blockListFragment = new BlockListFragment();
			debugFragment = new DebugFragment();
		}
		else
		{
			peerListFragment = (PeerListFragment) fm.findFragmentById(R.id.peer_list_fragment);
			blockListFragment = (BlockListFragment) fm.findFragmentById(R.id.block_list_fragment);
		}
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		int id = item.getItemId();
		if (id == android.R.id.home) {
			finish();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private class PagerAdapter extends FragmentStatePagerAdapter
	{
		public PagerAdapter(final FragmentManager fm)
		{
			super(fm);
		}

		@Override
		public int getCount()
		{
			return 3;
		}

		@Override
		public Fragment getItem(final int position)
		{
			if (position == 0)
				return peerListFragment;
			else if (position == 1)
				return blockListFragment;
			else
				return debugFragment;
		}
	}

	// -------------------------------------------------------------------------
	// Debug tab
	// -------------------------------------------------------------------------

	public static final class DebugFragment extends Fragment
	{
		private AbstractWalletActivity activity;
		private BlockchainService service;
		private TextView debugTextView;

		private final Handler handler = new Handler();
		private static final long REFRESH_MS = 2000;
		private static final SimpleDateFormat TIME_FMT =
				new SimpleDateFormat("HH:mm:ss", Locale.US);

		@Override
		public void onAttach(final Activity activity)
		{
			super.onAttach(activity);
			this.activity = (AbstractWalletActivity) activity;
		}

		@Override
		public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
				final Bundle savedInstanceState)
		{
			// Build layout programmatically: root → button row → scroll+textview
			final LinearLayout root = new LinearLayout(activity);
			root.setOrientation(LinearLayout.VERTICAL);

			// --- Button row ---
			final LinearLayout buttonRow = new LinearLayout(activity);
			buttonRow.setOrientation(LinearLayout.HORIZONTAL);

			final Button copyBtn = new Button(activity);
			copyBtn.setText("COPY LOG");
			copyBtn.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(final View v)
				{
					final ClipboardManager cm =
							(ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
					cm.setPrimaryClip(ClipData.newPlainText("dobbscoin-debug",
							debugTextView.getText()));
					Toast.makeText(activity, "Copied to clipboard", Toast.LENGTH_SHORT).show();
				}
			});

			final Button clearLogBtn = new Button(activity);
			clearLogBtn.setText("CLEAR LOG");
			clearLogBtn.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(final View v)
				{
					try
					{
						Runtime.getRuntime().exec("logcat -c");
						Toast.makeText(activity, "Logcat cleared", Toast.LENGTH_SHORT).show();
					}
					catch (final IOException e)
					{
						Toast.makeText(activity, "Clear failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
					}
				}
			});

			final Button reconnectBtn = new Button(activity);
			reconnectBtn.setText("FORCE RECONNECT");
			reconnectBtn.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(final View v)
				{
					// ACTION_RESET_BLOCKCHAIN exists in BlockchainServiceImpl.onStartCommand:
					// sets resetBlockchainOnShutdown=true and calls stopSelf(), then the
					// AlarmManager reschedules a fresh sync. This re-downloads the chain.
					final Intent intent = new Intent(BlockchainService.ACTION_RESET_BLOCKCHAIN,
							null, activity, BlockchainServiceImpl.class);
					activity.startService(intent);
					Toast.makeText(activity, "Blockchain reset requested — resync will begin shortly",
							Toast.LENGTH_LONG).show();
				}
			});

			final LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
					0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
			buttonRow.addView(copyBtn, btnParams);
			buttonRow.addView(clearLogBtn, btnParams);
			buttonRow.addView(reconnectBtn, btnParams);
			root.addView(buttonRow, new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT));

			// --- ScrollView + monospace TextView ---
			final ScrollView scrollView = new ScrollView(activity);
			debugTextView = new TextView(activity);
			debugTextView.setTypeface(Typeface.MONOSPACE);
			debugTextView.setTextSize(10f);
			debugTextView.setPadding(8, 8, 8, 8);
			debugTextView.setText("Waiting for service...");
			scrollView.addView(debugTextView, new ViewGroup.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT));
			root.addView(scrollView, new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

			return root;
		}

		@Override
		public void onActivityCreated(final Bundle savedInstanceState)
		{
			super.onActivityCreated(savedInstanceState);
			activity.bindService(new Intent(activity, BlockchainServiceImpl.class),
					serviceConnection, Context.BIND_AUTO_CREATE);
		}

		@Override
		public void onResume()
		{
			super.onResume();

			final IntentFilter filter = new IntentFilter();
			filter.addAction(BlockchainService.ACTION_PEER_STATE);
			filter.addAction(BlockchainService.ACTION_BLOCKCHAIN_STATE);
			LocalBroadcastManager.getInstance(activity).registerReceiver(broadcastReceiver, filter);

			scheduleRefresh();
		}

		@Override
		public void onPause()
		{
			handler.removeCallbacksAndMessages(null);
			LocalBroadcastManager.getInstance(activity).unregisterReceiver(broadcastReceiver);
			super.onPause();
		}

		@Override
		public void onDestroy()
		{
			activity.unbindService(serviceConnection);
			super.onDestroy();
		}

		private void scheduleRefresh()
		{
			handler.postDelayed(refreshRunnable, REFRESH_MS);
		}

		private final Runnable refreshRunnable = new Runnable()
		{
			@Override
			public void run()
			{
				updateDebugText();
				scheduleRefresh();
			}
		};

		private void updateDebugText()
		{
			if (debugTextView == null)
				return;

			final StringBuilder sb = new StringBuilder();

			sb.append("=== DOBBSCOIN DEBUG ===\n");
			sb.append("Time: ").append(TIME_FMT.format(new Date())).append("\n");

			// --- PeerGroup ---
			sb.append("\n--- PeerGroup ---\n");
			if (service != null)
			{
				final List<Peer> peers = service.getConnectedPeers();
				if (peers == null || peers.isEmpty())
				{
					sb.append("Connected peers: 0\n");
				}
				else
				{
					sb.append("Connected peers: ").append(peers.size()).append("\n");
					for (final Peer peer : peers)
					{
						final long ping = peer.getPingTime();
						sb.append("  ")
						  .append(peer.getAddress().getAddr().getHostAddress())
						  .append(":").append(peer.getAddress().getPort())
						  .append("  height=").append(peer.getBestHeight())
						  .append("  ping=")
						  .append(ping == Long.MAX_VALUE ? "?" : Long.toString(ping) + "ms")
						  .append("\n");
					}
				}
			}
			else
			{
				sb.append("Connected peers: (service not bound)\n");
			}

			// --- Discovery ---
			sb.append("\n--- Discovery ---\n");
			sb.append("DNS seeds:\n");
			sb.append("  seed.dobbscoin.info\n");
			sb.append("  node1.dobbscoin.info\n");
			sb.append("  node2.dobbscoin.info\n");

			// --- Blockchain ---
			sb.append("\n--- Blockchain ---\n");
			if (service != null)
			{
				final BlockchainState state = service.getBlockchainState();
				if (state != null)
				{
					sb.append("Best chain height: ").append(state.bestChainHeight).append("\n");
					sb.append("Best chain date:   ").append(state.bestChainDate).append("\n");
					sb.append("Replaying:         ").append(state.replaying).append("\n");

					// --- Network ---
					sb.append("\n--- Network ---\n");
					sb.append("Impediments: ");
					if (state.impediments.isEmpty())
						sb.append("none\n");
					else
						sb.append(state.impediments.toString()).append("\n");
				}
				else
				{
					sb.append("(state unavailable)\n");
					sb.append("\n--- Network ---\n");
					sb.append("Impediments: (unknown)\n");
				}
			}
			else
			{
				sb.append("(service not bound)\n");
				sb.append("\n--- Network ---\n");
				sb.append("Impediments: (unknown)\n");
			}

			// --- Service ---
			sb.append("\n--- Service ---\n");
			sb.append("BlockchainService: ")
			  .append(service != null ? "bound" : "not bound")
			  .append("\n");

			// --- Log ---
			sb.append("\n--- Log ---\n");
			try
			{
				final Process p = Runtime.getRuntime().exec(
						new String[]{"logcat", "-d", "-t", "50", "-s", "BlockchainServiceImpl:W"});
				final BufferedReader br = new BufferedReader(
						new InputStreamReader(p.getInputStream()));
				final List<String> matched = new ArrayList<String>();
				String line;
				while ((line = br.readLine()) != null)
				{
					if (line.contains("DOBBS") || line.contains("PeerGroup")
							|| line.contains("peer") || line.contains("connect")
							|| line.contains("discovery"))
					{
						matched.add(line);
					}
				}
				br.close();
				final int start = Math.max(0, matched.size() - 20);
				for (int i = start; i < matched.size(); i++)
					sb.append(matched.get(i)).append("\n");
				if (matched.isEmpty())
					sb.append("(no matching log lines)\n");
			}
			catch (final IOException e)
			{
				sb.append("(logcat error: ").append(e.getMessage()).append(")\n");
			}

			debugTextView.setText(sb.toString());
		}

		private final ServiceConnection serviceConnection = new ServiceConnection()
		{
			@Override
			public void onServiceConnected(final ComponentName name, final IBinder binder)
			{
				service = ((BlockchainServiceImpl.LocalBinder) binder).getService();
			}

			@Override
			public void onServiceDisconnected(final ComponentName name)
			{
				service = null;
			}
		};

		private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(final Context context, final Intent intent)
			{
				updateDebugText();
			}
		};
	}
}
