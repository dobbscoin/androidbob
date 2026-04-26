/*
 * Copyright 2015 the original author or authors.
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

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.WholeStringBuilder;
import subgeneius.dobbs.wallet.R;

/**
 * @author Andreas Schildbach
 */
public class ArchiveBackupDialogFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = ArchiveBackupDialogFragment.class.getName();

	private static final String KEY_FILE = "file";

	public static void show(final FragmentManager fm, final File backupFile)
	{
		final ArchiveBackupDialogFragment fragment = new ArchiveBackupDialogFragment();
		final Bundle args = new Bundle();
		args.putSerializable(KEY_FILE, backupFile);
		fragment.setArguments(args);
		fragment.show(fm, FRAGMENT_TAG);
	}

	private WalletActivity activity;

	private static final Logger log = LoggerFactory.getLogger(ArchiveBackupDialogFragment.class);

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);
		this.activity = (WalletActivity) activity;
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final File backupFile = (File) getArguments().getSerializable(KEY_FILE);

		final String backupPath = backupFile.getAbsolutePath();
		final String storagePath = Constants.Files.EXTERNAL_STORAGE_DIR.getAbsolutePath();
		final String path = backupPath.startsWith(storagePath)
				? backupPath.substring(storagePath.length())
				: backupPath;

		final DialogBuilder dialog = new DialogBuilder(activity);
		dialog.setMessage(Html.fromHtml(getString(R.string.export_keys_dialog_success, path)));
		dialog.setPositiveButton(WholeStringBuilder.bold(getString(R.string.export_keys_dialog_button_archive)),
				new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(final DialogInterface dialog, final int which)
					{
						log.info("user tapped Archive — handing off to WalletActivity for Drive upload");
						activity.startDriveBackup(backupFile);
					}
				});
		dialog.setNegativeButton(R.string.button_dismiss, null);

		return dialog.create();
	}
}
