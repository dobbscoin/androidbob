/*
 * Copyright 2011-2015 the original author or authors.
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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.VersionedChecksummedBytes;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.core.Wallet.BalanceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import android.app.KeyguardManager;

import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.wallet.DeterministicSeed;
import org.spongycastle.crypto.params.KeyParameter;

import java.util.List;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.services.drive.DriveScopes;
import com.google.common.base.Charsets;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.DriveBackupHelper;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.ui.InputParser.BinaryInputParser;
import de.schildbach.wallet.ui.InputParser.StringInputParser;
import de.schildbach.wallet.ui.preference.PreferenceActivity;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet.ui.send.SweepWalletActivity;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet.util.Crypto;
import de.schildbach.wallet.util.HttpGetThread;
import de.schildbach.wallet.util.Io;
import de.schildbach.wallet.util.Nfc;
import de.schildbach.wallet.util.WalletUtils;
import subgeneius.dobbs.wallet.R;

/**
 * @author Andreas Schildbach
 */
public final class WalletActivity extends AbstractWalletActivity
{
	private static final Logger log = LoggerFactory.getLogger(WalletActivity.class);

	private static final int DIALOG_RESTORE_WALLET = 0;
	private static final int DIALOG_TIMESKEW_ALERT = 1;
	private static final int DIALOG_VERSION_ALERT = 2;
	private static final int DIALOG_LOW_STORAGE_ALERT = 3;

	private WalletApplication application;
	private Configuration config;
	private Wallet wallet;

	private Handler handler = new Handler();

	private static final int REQUEST_CODE_SCAN = 0;
	private static final int REQUEST_CODE_DRIVE_SIGN_IN = 1;
	private static final int REQUEST_CODE_CONFIRM_CREDENTIALS = 2;

	private File pendingDriveBackupFile;
	private boolean seedRequestNeedsWalletPassword;
	private String pendingCredentialAction; // "export_seed" or "import_seed"

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		application = getWalletApplication();
		application.writeDebugLog("A1:WalletActivity.onCreate savedState=" + (savedInstanceState != null));
		config = application.getConfiguration();
		wallet = application.getWallet();
		application.writeDebugLog("A3:got wallet txCount=" + wallet.getTransactions(true).size());

		setContentView(R.layout.wallet_content);
		application.writeDebugLog("A4:setContentView done");

		if (savedInstanceState == null)
		{
			application.writeDebugLog("A5:calling checkCrashTrace");
			checkCrashTrace();
			application.writeDebugLog("A6:calling checkAlerts");
			checkAlerts();
			application.writeDebugLog("A7:checkAlerts done");
		}

		config.touchLastUsed();
		application.writeDebugLog("A8:touchLastUsed done");

		handleIntent(getIntent());
		application.writeDebugLog("A9:handleIntent done");

		MaybeMaintenanceFragment.add(getFragmentManager());
		application.writeDebugLog("A10:onCreate complete");
	}

	@Override
	protected void onResume()
	{
		application.writeDebugLog("A11:onResume start");
		super.onResume();

		handler.postDelayed(new Runnable()
		{
			@Override
			public void run()
			{
				// delayed start so that UI has enough time to initialize
				application.writeDebugLog("A12:startBlockchainService (delayed)");
				getWalletApplication().startBlockchainService(true);
				application.writeDebugLog("A13:startBlockchainService returned");
			}
		}, 1000);

		checkLowStorageAlert();
		application.writeDebugLog("A14:onResume complete");
	}

	@Override
	protected void onPause()
	{
		handler.removeCallbacksAndMessages(null);

		super.onPause();
	}

	@Override
	protected void onNewIntent(final Intent intent)
	{
		handleIntent(intent);
	}

	private void handleIntent(final Intent intent)
	{
		final String action = intent.getAction();

		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action))
		{
			final String inputType = intent.getType();
			final NdefMessage ndefMessage = (NdefMessage) intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)[0];
			final byte[] input = Nfc.extractMimePayload(Constants.MIMETYPE_TRANSACTION, ndefMessage);

			new BinaryInputParser(inputType, input)
			{
				@Override
				protected void handlePaymentIntent(final PaymentIntent paymentIntent)
				{
					cannotClassify(inputType);
				}

				@Override
				protected void error(final int messageResId, final Object... messageArgs)
				{
					dialog(WalletActivity.this, null, 0, messageResId, messageArgs);
				}
			}.parse();
		}
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent intent)
	{
		if (requestCode == REQUEST_CODE_CONFIRM_CREDENTIALS)
		{
			if (resultCode == Activity.RESULT_OK)
			{
				if ("import_seed".equals(pendingCredentialAction))
					onImportCredentialsConfirmed();
				else
					onCredentialsConfirmed();
			}
			pendingCredentialAction = null;
			// cancelled — do nothing
		}
		else if (requestCode == REQUEST_CODE_DRIVE_SIGN_IN)
		{
			if (resultCode == Activity.RESULT_OK)
			{
				GoogleSignIn.getSignedInAccountFromIntent(intent)
						.addOnSuccessListener(account -> {
							if (pendingDriveBackupFile != null)
							{
								uploadToDrive(pendingDriveBackupFile, account);
								pendingDriveBackupFile = null;
							}
						})
						.addOnFailureListener(e -> {
							log.error("Google sign-in failed", e);
							android.widget.Toast.makeText(WalletActivity.this,
									"Google sign-in failed: " + e.getMessage(),
									android.widget.Toast.LENGTH_LONG).show();
						});
			}
			else
			{
				android.widget.Toast.makeText(this, "Google sign-in cancelled",
						android.widget.Toast.LENGTH_SHORT).show();
			}
		}
		else if (requestCode == REQUEST_CODE_SCAN && resultCode == Activity.RESULT_OK)
		{
			final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

			new StringInputParser(input)
			{
				@Override
				protected void handlePaymentIntent(final PaymentIntent paymentIntent)
				{
					SendCoinsActivity.start(WalletActivity.this, paymentIntent);
				}

				@Override
				protected void handlePrivateKey(final VersionedChecksummedBytes key)
				{
					SweepWalletActivity.start(WalletActivity.this, key);
				}

				@Override
				protected void handleDirectTransaction(final Transaction tx) throws VerificationException
				{
					application.processDirectTransaction(tx);
				}

				@Override
				protected void error(final int messageResId, final Object... messageArgs)
				{
					dialog(WalletActivity.this, null, R.string.button_scan, messageResId, messageArgs);
				}
			}.parse();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		super.onCreateOptionsMenu(menu);

		getMenuInflater().inflate(R.menu.wallet_options, menu);

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu)
	{
		super.onPrepareOptionsMenu(menu);

		final Resources res = getResources();
		final String externalStorageState = Environment.getExternalStorageState();

		// exchange rates removed
		menu.findItem(R.id.wallet_options_restore_wallet).setEnabled(
				Environment.MEDIA_MOUNTED.equals(externalStorageState) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(externalStorageState));
		menu.findItem(R.id.wallet_options_backup_wallet).setEnabled(Environment.MEDIA_MOUNTED.equals(externalStorageState));
		menu.findItem(R.id.wallet_options_encrypt_keys).setTitle(
				wallet.isEncrypted() ? R.string.wallet_options_encrypt_keys_change : R.string.wallet_options_encrypt_keys_set);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		int id = item.getItemId();
		if (id == R.id.wallet_options_request) {
			handleRequestCoins();
			return true;
		} else if (id == R.id.wallet_options_send) {
			handleSendCoins();
			return true;
		} else if (id == R.id.wallet_options_scan) {
			handleScan();
			return true;
		} else if (id == R.id.wallet_options_address_book) {
			AddressBookActivity.start(this);
			return true;
		} else if (id == R.id.wallet_options_sweep_wallet) {
			SweepWalletActivity.start(this);
			return true;
		} else if (id == R.id.wallet_options_network_monitor) {
			startActivity(new Intent(this, NetworkMonitorActivity.class));
			return true;
		} else if (id == R.id.wallet_options_restore_wallet) {
			showDialog(DIALOG_RESTORE_WALLET);
			return true;
		} else if (id == R.id.wallet_options_backup_wallet) {
			handleBackupWallet();
			return true;
		} else if (id == R.id.wallet_options_seed_phrase) {
			handleSeedPhrase();
			return true;
		} else if (id == R.id.wallet_options_import_seed_phrase) {
			handleImportSeedPhrase();
			return true;
		} else if (id == R.id.wallet_options_encrypt_keys) {
			handleEncryptKeys();
			return true;
		} else if (id == R.id.wallet_options_preferences) {
			startActivity(new Intent(this, PreferenceActivity.class));
			return true;
		} else if (id == R.id.wallet_options_safety) {
			HelpDialogFragment.page(getFragmentManager(), R.string.help_safety);
			return true;
		} else if (id == R.id.wallet_options_help) {
			HelpDialogFragment.page(getFragmentManager(), R.string.help_wallet);
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	public void handleRequestCoins()
	{
		startActivity(new Intent(this, RequestCoinsActivity.class));
	}

	public void handleSendCoins()
	{
		startActivity(new Intent(this, SendCoinsActivity.class));
	}

	public void handleScan()
	{
		startActivityForResult(new Intent(this, ScanActivity.class), REQUEST_CODE_SCAN);
	}

	public void handleBackupWallet()
	{
		BackupWalletDialogFragment.show(getFragmentManager());
	}

	public void handleEncryptKeys()
	{
		EncryptKeysDialogFragment.show(getFragmentManager());
	}

	/** Called by ArchiveBackupDialogFragment when the user taps Archive. */
	void startDriveBackup(final File backupFile)
	{
		final Scope driveAppData = new Scope(DriveScopes.DRIVE_APPDATA);
		final GoogleSignInAccount existing = GoogleSignIn.getLastSignedInAccount(this);
		if (existing != null && GoogleSignIn.hasPermissions(existing, driveAppData))
		{
			uploadToDrive(backupFile, existing);
		}
		else
		{
			pendingDriveBackupFile = backupFile;
			final GoogleSignInOptions opts = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
					.requestScopes(driveAppData)
					.build();
			final GoogleSignInClient client = GoogleSignIn.getClient(this, opts);
			startActivityForResult(client.getSignInIntent(), REQUEST_CODE_DRIVE_SIGN_IN);
		}
	}

	private void handleSeedPhrase()
	{
		pendingCredentialAction = "export_seed";
		seedRequestNeedsWalletPassword = wallet.isEncrypted();

		@SuppressWarnings("deprecation")
		final KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
		if (km != null && km.isKeyguardSecure())
		{
			@SuppressWarnings("deprecation")
			final Intent credIntent = km.createConfirmDeviceCredentialIntent(
					getString(R.string.seed_phrase_auth_title),
					getString(R.string.seed_phrase_auth_desc));
			if (credIntent != null)
			{
				startActivityForResult(credIntent, REQUEST_CODE_CONFIRM_CREDENTIALS);
				return;
			}
		}
		// No device lock set — proceed but warn
		onCredentialsConfirmed();
	}

	private void onCredentialsConfirmed()
	{
		if (seedRequestNeedsWalletPassword)
		{
			final android.widget.EditText passwordView = new android.widget.EditText(this);
			passwordView.setInputType(android.text.InputType.TYPE_CLASS_TEXT
					| android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
			passwordView.setHint("Wallet password");

			new AlertDialog.Builder(this)
					.setTitle(R.string.seed_phrase_wallet_password_title)
					.setMessage(R.string.seed_phrase_wallet_password_msg)
					.setView(passwordView)
					.setPositiveButton(R.string.button_ok, (d, w) -> {
						final String pw = passwordView.getText().toString();
						passwordView.setText(null);
						displaySeedPhrase(pw);
					})
					.setNegativeButton(R.string.button_cancel, null)
					.show();
		}
		else
		{
			displaySeedPhrase(null);
		}
	}

	private void displaySeedPhrase(final String password)
	{
		final DeterministicSeed encSeed = wallet.getKeyChainSeed();
		if (encSeed == null)
		{
			showNoSeedDialog();
			return;
		}

		if (wallet.isEncrypted() && password != null)
		{
			// Key derivation is expensive — do it off the main thread
			final KeyCrypter crypter = wallet.getKeyCrypter();
			new Thread(() -> {
				try
				{
					final KeyParameter aesKey = crypter.deriveKey(password);
					final DeterministicSeed seed = encSeed.decrypt(crypter, "", aesKey);
					final List<String> words = seed.getMnemonicCode();
					runOnUiThread(() -> {
						if (words == null || words.isEmpty())
							showNoSeedDialog();
						else
							showSeedWordsDialog(words);
					});
				}
				catch (final Exception e)
				{
					log.error("seed phrase decryption failed", e);
					runOnUiThread(() -> android.widget.Toast.makeText(WalletActivity.this,
							R.string.seed_phrase_wrong_password, android.widget.Toast.LENGTH_LONG).show());
				}
			}).start();
		}
		else
		{
			final List<String> words = encSeed.getMnemonicCode();
			if (words == null || words.isEmpty())
				showNoSeedDialog();
			else
				showSeedWordsDialog(words);
		}
	}

	private void showSeedWordsDialog(final List<String> words)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append(getString(R.string.seed_phrase_dialog_intro)).append("\n\n");
		for (int i = 0; i < words.size(); i++)
			sb.append(String.format("%2d.  %s\n", i + 1, words.get(i)));

		new AlertDialog.Builder(this)
				.setTitle(R.string.seed_phrase_dialog_title)
				.setMessage(sb.toString())
				.setPositiveButton(R.string.button_ok, null)
				.show();
	}

	private void showNoSeedDialog()
	{
		new AlertDialog.Builder(this)
				.setTitle(R.string.seed_phrase_dialog_title)
				.setMessage(R.string.seed_phrase_no_seed)
				.setPositiveButton(R.string.button_ok, null)
				.show();
	}

	private void handleImportSeedPhrase()
	{
		new AlertDialog.Builder(this)
				.setTitle(R.string.import_seed_phrase_warning_title)
				.setMessage(R.string.import_seed_phrase_warning_msg)
				.setPositiveButton(R.string.button_ok, (d, w) -> confirmImportSeedWithAuth())
				.setNegativeButton(R.string.button_cancel, null)
				.show();
	}

	private void confirmImportSeedWithAuth()
	{
		pendingCredentialAction = "import_seed";
		@SuppressWarnings("deprecation")
		final KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
		if (km != null && km.isKeyguardSecure())
		{
			@SuppressWarnings("deprecation")
			final Intent credIntent = km.createConfirmDeviceCredentialIntent(
					getString(R.string.seed_phrase_auth_title),
					getString(R.string.import_seed_auth_desc));
			if (credIntent != null)
			{
				startActivityForResult(credIntent, REQUEST_CODE_CONFIRM_CREDENTIALS);
				return;
			}
		}
		onImportCredentialsConfirmed();
	}

	private void onImportCredentialsConfirmed()
	{
		final android.widget.EditText input = new android.widget.EditText(this);
		input.setHint("word1 word2 word3 …");
		input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
				| android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
				| android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		input.setMinLines(3);

		new AlertDialog.Builder(this)
				.setTitle(R.string.import_seed_phrase_dialog_title)
				.setMessage(R.string.import_seed_phrase_dialog_msg)
				.setView(input)
				.setPositiveButton(R.string.button_ok, (d, w) -> {
					final String text = input.getText().toString().trim().toLowerCase();
					input.setText(null);
					final String[] parts = text.split("\\s+");
					validateAndImportSeed(java.util.Arrays.asList(parts));
				})
				.setNegativeButton(R.string.button_cancel, (d, w) -> input.setText(null))
				.show();
	}

	private void validateAndImportSeed(final List<String> words)
	{
		new Thread(() -> {
			try
			{
				org.bitcoinj.crypto.MnemonicCode.INSTANCE.check(words);
				final org.bitcoinj.wallet.DeterministicSeed seed =
						new org.bitcoinj.wallet.DeterministicSeed(words, null, "", 0);
				final Wallet newWallet = Wallet.fromSeed(Constants.NETWORK_PARAMETERS, seed);
				runOnUiThread(() -> {
					try { restoreWallet(newWallet); }
					catch (final IOException e) { log.error("seed restore failed", e); }
				});
			}
			catch (final org.bitcoinj.crypto.MnemonicException e)
			{
				log.error("invalid mnemonic on import", e);
				runOnUiThread(() -> android.widget.Toast.makeText(WalletActivity.this,
						R.string.import_seed_phrase_invalid, android.widget.Toast.LENGTH_LONG).show());
			}
		}).start();
	}

	private void uploadToDrive(final File backupFile, final GoogleSignInAccount account)
	{
		new Thread(() -> {
			try
			{
				DriveBackupHelper.upload(getApplicationContext(), account, backupFile);
				log.info("wallet backup uploaded to Google Drive: {}", backupFile.getName());
				runOnUiThread(() -> android.widget.Toast.makeText(WalletActivity.this,
						"Backup saved to Google Drive", android.widget.Toast.LENGTH_LONG).show());
			}
			catch (final Exception e)
			{
				log.error("Drive backup failed", e);
				runOnUiThread(() -> android.widget.Toast.makeText(WalletActivity.this,
						"Drive backup failed: " + e.getMessage(),
						android.widget.Toast.LENGTH_LONG).show());
			}
		}).start();
	}

	@Override
	protected Dialog onCreateDialog(final int id, final Bundle args)
	{
		if (id == DIALOG_RESTORE_WALLET)
			return createRestoreWalletDialog();
		else if (id == DIALOG_TIMESKEW_ALERT)
			return createTimeskewAlertDialog(args.getLong("diff_minutes"));
		else if (id == DIALOG_VERSION_ALERT)
			return createVersionAlertDialog();
		else if (id == DIALOG_LOW_STORAGE_ALERT)
			return createLowStorageAlertDialog();
		else
			throw new IllegalArgumentException();
	}

	@Override
	protected void onPrepareDialog(final int id, final Dialog dialog)
	{
		if (id == DIALOG_RESTORE_WALLET)
			prepareRestoreWalletDialog(dialog);
	}

	private Dialog createRestoreWalletDialog()
	{
		final View view = getLayoutInflater().inflate(R.layout.restore_wallet_dialog, null);
		final TextView messageView = (TextView) view.findViewById(R.id.restore_wallet_dialog_message);
		final Spinner fileView = (Spinner) view.findViewById(R.id.import_keys_from_storage_file);
		final EditText passwordView = (EditText) view.findViewById(R.id.import_keys_from_storage_password);

		final DialogBuilder dialog = new DialogBuilder(this);
		dialog.setTitle(R.string.import_keys_dialog_title);
		dialog.setView(view);
		dialog.setPositiveButton(R.string.import_keys_dialog_button_import, new OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				final File file = (File) fileView.getSelectedItem();
				final String password = passwordView.getText().toString().trim();
				passwordView.setText(null); // get rid of it asap

				if (WalletUtils.BACKUP_FILE_FILTER.accept(file))
					restoreWalletFromProtobuf(file);
				else if (WalletUtils.KEYS_FILE_FILTER.accept(file))
					restorePrivateKeysFromBase58(file);
				else if (Crypto.OPENSSL_FILE_FILTER.accept(file))
					restoreWalletFromEncrypted(file, password);
			}
		});
		dialog.setNegativeButton(R.string.button_cancel, new OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				passwordView.setText(null); // get rid of it asap
			}
		});
		dialog.setOnCancelListener(new OnCancelListener()
		{
			@Override
			public void onCancel(final DialogInterface dialog)
			{
				passwordView.setText(null); // get rid of it asap
			}
		});

		final FileAdapter adapter = new FileAdapter(this)
		{
			@Override
			public View getDropDownView(final int position, View row, final ViewGroup parent)
			{
				final File file = getItem(position);
				final boolean isExternal = Constants.Files.EXTERNAL_WALLET_BACKUP_DIR.equals(file.getParentFile());
				final boolean isEncrypted = Crypto.OPENSSL_FILE_FILTER.accept(file);

				if (row == null)
					row = inflater.inflate(R.layout.restore_wallet_file_row, null);

				final TextView filenameView = (TextView) row.findViewById(R.id.wallet_import_keys_file_row_filename);
				filenameView.setText(file.getName());

				final TextView securityView = (TextView) row.findViewById(R.id.wallet_import_keys_file_row_security);
				final String encryptedStr = context.getString(isEncrypted ? R.string.import_keys_dialog_file_security_encrypted
						: R.string.import_keys_dialog_file_security_unencrypted);
				final String storageStr = context.getString(isExternal ? R.string.import_keys_dialog_file_security_external
						: R.string.import_keys_dialog_file_security_internal);
				securityView.setText(encryptedStr + ", " + storageStr);

				final TextView createdView = (TextView) row.findViewById(R.id.wallet_import_keys_file_row_created);
				createdView
						.setText(context.getString(isExternal ? R.string.import_keys_dialog_file_created_manual
								: R.string.import_keys_dialog_file_created_automatic, DateUtils.getRelativeTimeSpanString(context,
								file.lastModified(), true)));

				return row;
			}
		};

		final String path;
		final String backupPath = Constants.Files.EXTERNAL_WALLET_BACKUP_DIR.getAbsolutePath();
		final String storagePath = Constants.Files.EXTERNAL_STORAGE_DIR.getAbsolutePath();
		if (backupPath.startsWith(storagePath))
			path = backupPath.substring(storagePath.length());
		else
			path = backupPath;
		messageView.setText(getString(R.string.import_keys_dialog_message, path));

		fileView.setAdapter(adapter);

		return dialog.create();
	}

	private void prepareRestoreWalletDialog(final Dialog dialog)
	{
		final AlertDialog alertDialog = (AlertDialog) dialog;

		final List<File> files = new LinkedList<File>();

		// external storage
		if (Constants.Files.EXTERNAL_WALLET_BACKUP_DIR.exists() && Constants.Files.EXTERNAL_WALLET_BACKUP_DIR.isDirectory())
			for (final File file : Constants.Files.EXTERNAL_WALLET_BACKUP_DIR.listFiles())
				if (Crypto.OPENSSL_FILE_FILTER.accept(file))
					files.add(file);

		// internal storage
		for (final String filename : fileList())
			if (filename.startsWith(Constants.Files.WALLET_KEY_BACKUP_PROTOBUF + '.'))
				files.add(new File(getFilesDir(), filename));

		// sort
		Collections.sort(files, new Comparator<File>()
		{
			@Override
			public int compare(final File lhs, final File rhs)
			{
				return lhs.getName().compareToIgnoreCase(rhs.getName());
			}
		});

		final View replaceWarningView = alertDialog.findViewById(R.id.restore_wallet_from_storage_dialog_replace_warning);
		final boolean hasCoins = wallet.getBalance(BalanceType.ESTIMATED).signum() > 0;
		replaceWarningView.setVisibility(hasCoins ? View.VISIBLE : View.GONE);

		final Spinner fileView = (Spinner) alertDialog.findViewById(R.id.import_keys_from_storage_file);
		final FileAdapter adapter = (FileAdapter) fileView.getAdapter();
		adapter.setFiles(files);
		fileView.setEnabled(!adapter.isEmpty());

		final EditText passwordView = (EditText) alertDialog.findViewById(R.id.import_keys_from_storage_password);
		passwordView.setText(null);

		final ImportDialogButtonEnablerListener dialogButtonEnabler = new ImportDialogButtonEnablerListener(passwordView, alertDialog)
		{
			@Override
			protected boolean hasFile()
			{
				return fileView.getSelectedItem() != null;
			}

			@Override
			protected boolean needsPassword()
			{
				final File selectedFile = (File) fileView.getSelectedItem();
				return selectedFile != null ? Crypto.OPENSSL_FILE_FILTER.accept(selectedFile) : false;
			}
		};
		passwordView.addTextChangedListener(dialogButtonEnabler);
		fileView.setOnItemSelectedListener(dialogButtonEnabler);

		final CheckBox showView = (CheckBox) alertDialog.findViewById(R.id.import_keys_from_storage_show);
		showView.setOnCheckedChangeListener(new ShowPasswordCheckListener(passwordView));
	}

	private void checkLowStorageAlert()
	{
		final Intent stickyIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW));
		if (stickyIntent != null)
			showDialog(DIALOG_LOW_STORAGE_ALERT);
	}

	private Dialog createLowStorageAlertDialog()
	{
		final DialogBuilder dialog = DialogBuilder.warn(this, R.string.wallet_low_storage_dialog_title);
		dialog.setMessage(R.string.wallet_low_storage_dialog_msg);
		dialog.setPositiveButton(R.string.wallet_low_storage_dialog_button_apps, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int id)
			{
				startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
				finish();
			}
		});
		dialog.setNegativeButton(R.string.button_dismiss, null);
		return dialog.create();
	}

	private void checkCrashTrace()
	{
		if (CrashReporter.hasSavedCrashTrace())
		{
			final StringBuilder stackTrace = new StringBuilder();
			try
			{
				CrashReporter.appendSavedCrashTrace(stackTrace);
			}
			catch (final IOException x)
			{
				log.info("problem reading crash trace", x);
			}
			log.warn("CRASH TRACE FROM PREVIOUS RUN:\n" + stackTrace);
		}
	}

	private void checkAlerts()
	{
		if (Constants.VERSION_URL.isEmpty())
			return;

		final PackageInfo packageInfo = getWalletApplication().packageInfo();
		final int versionNameSplit = packageInfo.versionName.indexOf('-');
		final String base = Constants.VERSION_URL + (versionNameSplit >= 0 ? packageInfo.versionName.substring(versionNameSplit) : "");
		final String url = base + "?package=" + packageInfo.packageName + "&current=" + packageInfo.versionCode;

		new HttpGetThread(getAssets(), url, application.httpUserAgent())
		{
			@Override
			protected void handleLine(final String line, final long serverTime)
			{
				final int serverVersionCode = Integer.parseInt(line.split("\\s+")[0]);

				log.info("according to \"" + url + "\", strongly recommended minimum app version is " + serverVersionCode);

				if (serverTime > 0)
				{
					final long diffMinutes = Math.abs((System.currentTimeMillis() - serverTime) / DateUtils.MINUTE_IN_MILLIS);

					if (diffMinutes >= 60)
					{
						log.info("according to \"" + url + "\", system clock is off by " + diffMinutes + " minutes");

						runOnUiThread(new Runnable()
						{
							@Override
							public void run()
							{
								final Bundle args = new Bundle();
								args.putLong("diff_minutes", diffMinutes);
								showDialog(DIALOG_TIMESKEW_ALERT, args);
							}
						});

						return;
					}
				}

				if (serverVersionCode > packageInfo.versionCode)
				{
					runOnUiThread(new Runnable()
					{
						@Override
						public void run()
						{
							showDialog(DIALOG_VERSION_ALERT);
						}
					});

					return;
				}
			}

			@Override
			protected void handleException(final Exception x)
			{
				if (x instanceof UnknownHostException || x instanceof SocketException || x instanceof SocketTimeoutException)
				{
					// swallow
					log.debug("problem reading", x);
				}
				else
				{
					CrashReporter.saveBackgroundTrace(new RuntimeException(url, x), packageInfo);
				}
			}
		}.start();

		if (CrashReporter.hasSavedCrashTrace())
		{
			final StringBuilder stackTrace = new StringBuilder();

			try
			{
				CrashReporter.appendSavedCrashTrace(stackTrace);
			}
			catch (final IOException x)
			{
				log.info("problem appending crash info", x);
			}

			final ReportIssueDialogBuilder dialog = new ReportIssueDialogBuilder(this, R.string.report_issue_dialog_title_crash,
					R.string.report_issue_dialog_message_crash)
			{
				@Override
				protected CharSequence subject()
				{
					return Constants.REPORT_SUBJECT_CRASH + " " + packageInfo.versionName;
				}

				@Override
				protected CharSequence collectApplicationInfo() throws IOException
				{
					final StringBuilder applicationInfo = new StringBuilder();
					CrashReporter.appendApplicationInfo(applicationInfo, application);
					return applicationInfo;
				}

				@Override
				protected CharSequence collectStackTrace() throws IOException
				{
					if (stackTrace.length() > 0)
						return stackTrace;
					else
						return null;
				}

				@Override
				protected CharSequence collectDeviceInfo() throws IOException
				{
					final StringBuilder deviceInfo = new StringBuilder();
					CrashReporter.appendDeviceInfo(deviceInfo, WalletActivity.this);
					return deviceInfo;
				}

				@Override
				protected CharSequence collectWalletDump()
				{
					return wallet.toString(false, true, true, null);
				}
			};

			dialog.show();
		}
	}

	private Dialog createTimeskewAlertDialog(final long diffMinutes)
	{
		final PackageManager pm = getPackageManager();
		final Intent settingsIntent = new Intent(android.provider.Settings.ACTION_DATE_SETTINGS);

		final DialogBuilder dialog = DialogBuilder.warn(this, R.string.wallet_timeskew_dialog_title);
		dialog.setMessage(getString(R.string.wallet_timeskew_dialog_msg, diffMinutes));

		if (pm.resolveActivity(settingsIntent, 0) != null)
		{
			dialog.setPositiveButton(R.string.button_settings, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int id)
				{
					startActivity(settingsIntent);
					finish();
				}
			});
		}

		dialog.setNegativeButton(R.string.button_dismiss, null);
		return dialog.create();
	}

	private Dialog createVersionAlertDialog()
	{
		final PackageManager pm = getPackageManager();
		final Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Constants.MARKET_APP_URL, getPackageName())));
		final Intent binaryIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.BINARY_URL));

		final DialogBuilder dialog = DialogBuilder.warn(this, R.string.wallet_version_dialog_title);
		final StringBuilder message = new StringBuilder(getString(R.string.wallet_version_dialog_msg));
		if (Build.VERSION.SDK_INT < Constants.SDK_DEPRECATED_BELOW)
			message.append("\n\n").append(getString(R.string.wallet_version_dialog_msg_deprecated));
		dialog.setMessage(message);

		if (pm.resolveActivity(marketIntent, 0) != null)
		{
			dialog.setPositiveButton(R.string.wallet_version_dialog_button_market, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int id)
				{
					startActivity(marketIntent);
					finish();
				}
			});
		}

		if (pm.resolveActivity(binaryIntent, 0) != null)
		{
			dialog.setNeutralButton(R.string.wallet_version_dialog_button_binary, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int id)
				{
					startActivity(binaryIntent);
					finish();
				}
			});
		}

		dialog.setNegativeButton(R.string.button_dismiss, null);
		return dialog.create();
	}

	private void restoreWalletFromEncrypted(final File file, final String password)
	{
		try
		{
			final BufferedReader cipherIn = new BufferedReader(new InputStreamReader(new FileInputStream(file), Charsets.UTF_8));
			final StringBuilder cipherText = new StringBuilder();
			Io.copy(cipherIn, cipherText, Constants.BACKUP_MAX_CHARS);
			cipherIn.close();

			final byte[] plainText = Crypto.decryptBytes(cipherText.toString(), password.toCharArray());
			final InputStream is = new ByteArrayInputStream(plainText);

			restoreWallet(WalletUtils.restoreWalletFromProtobufOrBase58(is, Constants.NETWORK_PARAMETERS));

			log.info("successfully restored encrypted wallet: {}", file);
		}
		catch (final IOException x)
		{
			final DialogBuilder dialog = DialogBuilder.warn(this, R.string.import_export_keys_dialog_failure_title);
			dialog.setMessage(getString(R.string.import_keys_dialog_failure, x.getMessage()));
			dialog.setPositiveButton(R.string.button_dismiss, null);
			dialog.setNegativeButton(R.string.button_retry, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int id)
				{
					showDialog(DIALOG_RESTORE_WALLET);
				}
			});
			dialog.show();

			log.info("problem restoring wallet", x);
		}
	}

	private void restoreWalletFromProtobuf(final File file)
	{
		FileInputStream is = null;
		try
		{
			is = new FileInputStream(file);
			restoreWallet(WalletUtils.restoreWalletFromProtobuf(is, Constants.NETWORK_PARAMETERS));

			log.info("successfully restored unencrypted wallet: {}", file);
		}
		catch (final IOException x)
		{
			final DialogBuilder dialog = DialogBuilder.warn(this, R.string.import_export_keys_dialog_failure_title);
			dialog.setMessage(getString(R.string.import_keys_dialog_failure, x.getMessage()));
			dialog.setPositiveButton(R.string.button_dismiss, null);
			dialog.setNegativeButton(R.string.button_retry, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int id)
				{
					showDialog(DIALOG_RESTORE_WALLET);
				}
			});
			dialog.show();

			log.info("problem restoring wallet", x);
		}
		finally
		{
			if (is != null)
			{
				try
				{
					is.close();
				}
				catch (final IOException x2)
				{
					// swallow
				}
			}
		}
	}

	private void restorePrivateKeysFromBase58(final File file)
	{
		FileInputStream is = null;
		try
		{
			is = new FileInputStream(file);
			restoreWallet(WalletUtils.restorePrivateKeysFromBase58(is, Constants.NETWORK_PARAMETERS));

			log.info("successfully restored unencrypted private keys: {}", file);
		}
		catch (final IOException x)
		{
			final DialogBuilder dialog = DialogBuilder.warn(this, R.string.import_export_keys_dialog_failure_title);
			dialog.setMessage(getString(R.string.import_keys_dialog_failure, x.getMessage()));
			dialog.setPositiveButton(R.string.button_dismiss, null);
			dialog.setNegativeButton(R.string.button_retry, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int id)
				{
					showDialog(DIALOG_RESTORE_WALLET);
				}
			});
			dialog.show();

			log.info("problem restoring private keys", x);
		}
		finally
		{
			if (is != null)
			{
				try
				{
					is.close();
				}
				catch (final IOException x2)
				{
					// swallow
				}
			}
		}
	}

	private void restoreWallet(final Wallet wallet) throws IOException
	{
		application.replaceWallet(wallet);

		config.disarmBackupReminder();

		final DialogBuilder dialog = new DialogBuilder(this);
		final StringBuilder message = new StringBuilder();
		message.append(getString(R.string.restore_wallet_dialog_success));
		message.append("\n\n");
		message.append(getString(R.string.restore_wallet_dialog_success_replay));
		dialog.setMessage(message);
		dialog.setNeutralButton(R.string.button_ok, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int id)
			{
				getWalletApplication().resetBlockchain();
				finish();
			}
		});
		dialog.show();
	}
}
