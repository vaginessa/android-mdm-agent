/*
 *   Copyright (C) 2017 Teclib. All rights reserved.
 *
 * This file is part of flyve-mdm-android-agent
 *
 * flyve-mdm-android-agent is a subproject of Flyve MDM. Flyve MDM is a mobile
 * device management software.
 *
 * Flyve MDM is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * Flyve MDM is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * ------------------------------------------------------------------------------
 * @author    Rafael Hernandez
 * @date      02/06/2017
 * @copyright Copyright (C) ${YEAR} Teclib. All rights reserved.
 * @license   GPLv3 https://www.gnu.org/licenses/gpl-3.0.html
 * @link      https://github.com/flyve-mdm/flyve-mdm-android-agent
 * @link      https://flyve-mdm.com
 * ------------------------------------------------------------------------------
 */

package org.flyve.mdm.agent.utils;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class ConnectionHTTP {

	private static Handler uiHandler;

	static {
		uiHandler = new Handler(Looper.getMainLooper());
	}

	private static int timeout = 18000;
	private static int readtimeout = 6000;
	private static final String EXCEPTION_HTTP = "EXCEPTION_HTTP_";

	private static void runOnUI(Runnable runnable) {
		uiHandler.post(runnable);
	}

	/**
	 * Get data from url in a thread
	 * @param url String url
	 * @param method String POST, GET, PUT, DELETE
	 * @param callback DataCallback
	 */
	public static void getWebData(final String url, final String method, final DataCallback callback)
	{
		Thread t = new Thread(new Runnable()
		{
			public void run()
			{
				try
				{
					URL dataURL = new URL(url);
					FlyveLog.d(method + " " + url);
					HttpURLConnection conn = (HttpURLConnection)dataURL.openConnection();

					conn.setConnectTimeout(timeout);
					conn.setReadTimeout(readtimeout);
					conn.setInstanceFollowRedirects(true);

					if(conn.getResponseCode() >= 400) {
						InputStream is = conn.getErrorStream();
						final String result = inputStreamToString(is);

						ConnectionHTTP.runOnUI(new Runnable()
						{
							public void run()
							{
								callback.callback(result);
							}
						});
						return;
					}

					InputStream is = conn.getInputStream();

					final String result = inputStreamToString(is);
					FlyveLog.d("Request" + result);

					ConnectionHTTP.runOnUI(new Runnable() {
						public void run() {
							callback.callback(result);
						}
					});					

				}
				catch (final Exception ex)
				{
					ConnectionHTTP.runOnUI(new Runnable()
					{
						public void run()
						{
						callback.callback(ex.getMessage());
						FlyveLog.e(ex.getClass() +" : " + ex.getMessage());
						}
					});
				}
			}
		});
		t.start();
	}

	/**
	 * Get the data in a synchronous way
	 * @param url
	 * @param method
	 * @param header
	 */
	public static String getSyncWebData(String url, String method, Map<String, String> header)
	{
		try
		{
			URL dataURL = new URL(url);
			FlyveLog.d("Method: " + method + " - URL = " + url);
			HttpURLConnection conn = (HttpURLConnection)dataURL.openConnection();

			conn.setConnectTimeout(timeout);
			conn.setReadTimeout(readtimeout);
			conn.setInstanceFollowRedirects(true);
			conn.setRequestMethod(method);

			if(header != null) {
				for (Map.Entry<String, String> entry : header.entrySet()) {
					conn.setRequestProperty(entry.getKey(), entry.getValue());
					FlyveLog.d(entry.getKey() + " = " + entry.getValue());
				}
			}

			if(conn.getResponseCode() >= 400) {
				InputStream is = conn.getErrorStream();
				return inputStreamToString(is);
			}

			InputStream is = conn.getInputStream();

			final String result = inputStreamToString(is);
			FlyveLog.d("GetRequest input stream = " + result);

			return result;
		}
		catch (final Exception ex)
		{
			FlyveLog.e(ex.getClass() +" : " + ex.getMessage());
			return EXCEPTION_HTTP + ex.getMessage();
		}
	}

	/**
	 * Get the data in a synchronous way
	 * @param url the url
	 * @param data
	 * @param header
	 */
	public static String getSyncWebData(final String url, final JSONObject data, final Map<String, String> header) {
		try
		{
			URL dataURL = new URL(url);
			FlyveLog.i("getSyncWebData: " + url);
			HttpURLConnection conn = (HttpURLConnection)dataURL.openConnection();

			conn.setRequestMethod("POST");
			conn.setConnectTimeout(timeout);
			conn.setReadTimeout(readtimeout);

			for (Map.Entry<String, String> entry : header.entrySet()) {
				conn.setRequestProperty(entry.getKey(), entry.getValue());
				FlyveLog.d(entry.getKey() + " = " + entry.getValue());
			}

			// Send post request
			conn.setDoOutput(true);

			DataOutputStream os = new DataOutputStream(conn.getOutputStream());
			os.writeBytes(data.toString());
			os.flush();
			os.close();

			if(conn.getResponseCode() >= 400) {
				InputStream is = conn.getErrorStream();
				return inputStreamToString(is);
			}

			InputStream is = conn.getInputStream();
			return inputStreamToString(is);

		}
		catch (final Exception ex)
		{
			String error = EXCEPTION_HTTP + ex.getMessage();
			FlyveLog.e(error);
			return error;
		}
	}

	/**
	 * Download and save files on device
	 * @param url String the url to download the file
	 * @param pathFile String place to save
	 * @return Boolean if file is write
	 */
	public static Boolean getSyncFile(final String url, final String pathFile) {

		OutputStream output = null;

		try {
			URL dataURL = new URL(url);
			FlyveLog.d("getSyncFile: " + url);
			HttpURLConnection conn = (HttpURLConnection)dataURL.openConnection();

			conn.setConnectTimeout(timeout);
			conn.setReadTimeout(readtimeout);
			conn.setInstanceFollowRedirects(true);

			HashMap<String, String> header = new HashMap();
			header.put("Accept","application/octet-stream");
			header.put("Content-Type","application/json");

			for (Map.Entry<String, String> entry : header.entrySet()) {
				conn.setRequestProperty(entry.getKey(), entry.getValue());
				FlyveLog.d(entry.getKey() + " = " + entry.getValue());
			}

			int fileLength = conn.getContentLength();

			InputStream input = conn.getInputStream();
			output = new FileOutputStream(pathFile);

			byte[] data = new byte[4096];
			long total = 0;
			int count;

			while ((count = input.read(data)) != -1) {
				total += count;
				//publish progress only if total length is known
				if (fileLength > 0) {
					FlyveLog.v( String.valueOf (((int)(total * 100 / fileLength))));
				}
				output.write(data, 0, count);
			}
			return true;
		}
		catch (final Exception ex) {
			FlyveLog.e(ex.getClass() +" : " + ex.getMessage());
			return false;
		}
		finally {
			if(output!=null){
				try {
					output.close();
				} catch (Exception ex) {
					FlyveLog.e(ex.getMessage());
				}
			}
		}
	}

	/**
	 * Get data from url in a thread
	 * @param url String url
	 * @param method String POST, GET, PUT, DELETE
	 * @param header Map with al the header information
	 * @param callback DataCallback
	 */
	public static void getWebData(final String url, final String method, final Map<String, String> header, final DataCallback callback)
	{
		Thread t = new Thread(new Runnable()
		{
			public void run()
			{
				try
				{
					URL dataURL = new URL(url);
					FlyveLog.i(method + " " + url);
					HttpURLConnection conn = (HttpURLConnection)dataURL.openConnection();

					conn.setConnectTimeout(timeout);
					conn.setReadTimeout(readtimeout);
					conn.setInstanceFollowRedirects(true);
					conn.setRequestMethod(method);

					for (Map.Entry<String, String> entry : header.entrySet()) {
						conn.setRequestProperty(entry.getKey(), entry.getValue());
						FlyveLog.d(entry.getKey() + " = " + entry.getValue());
					}

					if(conn.getResponseCode() >= 400) {
						InputStream is = conn.getErrorStream();
						final String result = inputStreamToString(is);

						ConnectionHTTP.runOnUI(new Runnable()
						{
							public void run()
							{
								callback.callback(result);
							}
						});
						return;
					}

					InputStream is = conn.getInputStream();
					final String result = inputStreamToString(is);

					FlyveLog.d("GetRequest input stream = " + result);

					ConnectionHTTP.runOnUI(new Runnable() {
						public void run() {
							callback.callback(result);
						}
					});					

				}
				catch (final Exception ex)
				{
					ConnectionHTTP.runOnUI(new Runnable()
					{
						public void run()
						{
						callback.callback(EXCEPTION_HTTP + ex.getMessage());
						FlyveLog.e(ex.getClass() + " : " + ex.getMessage());
						}
					});
				}
			}
		});
		t.start();
	}

	/**
	 * Send information by post with a JSONObject and header
	 * @param url String url
	 * @param data JSONObject data to send
	 * @param header Map with al the header information
	 * @param callback DataCallback
	 */
	public static void getWebData(final String url, final JSONObject data, final Map<String, String> header, final DataCallback callback)
	{
		Thread t = new Thread(new Runnable()
		{
			public void run()
			{
			try
			{
				URL dataURL = new URL(url);
				FlyveLog.i("Method: POST - URL = " + url);
				HttpURLConnection conn = (HttpURLConnection)dataURL.openConnection();

				conn.setRequestMethod("POST");
				conn.setConnectTimeout(timeout);
				conn.setReadTimeout(readtimeout);

				for (Map.Entry<String, String> entry : header.entrySet()) {
					conn.setRequestProperty(entry.getKey(), entry.getValue());
					FlyveLog.d(entry.getKey() + " = " + entry.getValue());
				}

				// Send post request
				conn.setDoOutput(true);

				DataOutputStream os = new DataOutputStream(conn.getOutputStream());
				os.writeBytes(data.toString());
				os.flush();
				os.close();

				if(conn.getResponseCode() >= 400) {
					InputStream is = conn.getErrorStream();
					final String result = inputStreamToString(is);

					ConnectionHTTP.runOnUI(new Runnable()
					{
						public void run()
						{
							callback.callback(result);
						}
					});
					return;
				}

				InputStream is = conn.getInputStream();
				final String result = inputStreamToString(is);

				ConnectionHTTP.runOnUI(new Runnable() {
					public void run() {
						callback.callback(result);
					}
				});

			}
			catch (final Exception ex)
			{
				ConnectionHTTP.runOnUI(new Runnable()
				{
					public void run()
					{
						callback.callback(EXCEPTION_HTTP + ex.getMessage());
						FlyveLog.e(ex.getClass() + " : " + ex.getMessage());
					}
				});
			}
			}
		});
		t.start();
	}

	/**
	 * Convert inputStream to String
	 * @param stream InputStream to convert
	 * @return String converted
	 * @throws IOException error
	 */
	private static String inputStreamToString(final InputStream stream) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
		StringBuilder sb = new StringBuilder();
		String line = null;
		while ((line = br.readLine()) != null) {
			sb.append(line + "\n");
		}
		br.close();
		return sb.toString();
	}

	/**
	 * This is the return data interface
	 */
	public interface DataCallback {
		void callback(String data);
	}

}
