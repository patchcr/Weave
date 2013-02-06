/*
    Weave (Web-based Analysis and Visualization Environment)
    Copyright (C) 2008-2011 University of Massachusetts Lowell

    This file is a part of Weave.

    Weave is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License, Version 3,
    as published by the Free Software Foundation.

    Weave is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Weave.  If not, see <http://www.gnu.org/licenses/>.
*/

package weave.geometrystream;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import weave.utils.SQLUtils;

/**
 * Static methods for reading a geometry stream from a SQL table generated by an SQLGeometryStreamDestination.
 * 
 * @author adufilie
 */
public class SQLGeometryStreamReader
{
	public static byte[] getMetadataTileDescriptors(Connection conn, String schema, String tablePrefix)
		throws SQLException, IOException
	{
		return getTileDescriptors(conn, schema, tablePrefix + SQLGeometryStreamDestination.SQL_TABLE_METADATA_SUFFIX);
	}
	public static byte[] getGeometryTileDescriptors(Connection conn, String schema, String tablePrefix)
		throws SQLException, IOException
	{
		return getTileDescriptors(conn, schema, tablePrefix + SQLGeometryStreamDestination.SQL_TABLE_GEOMETRY_SUFFIX);
	}
	public static byte[] getMetadataTiles(Connection conn, String schema, String tablePrefix, List<Integer> tileIDs)
		throws SQLException, IOException
	{
		return getTiles(conn, schema, tablePrefix + SQLGeometryStreamDestination.SQL_TABLE_METADATA_SUFFIX, tileIDs);
	}
	public static byte[] getGeometryTiles(Connection conn, String schema, String tablePrefix, List<Integer> tileIDs)
		throws SQLException, IOException
	{
		return getTiles(conn, schema, tablePrefix + SQLGeometryStreamDestination.SQL_TABLE_GEOMETRY_SUFFIX, tileIDs);
	}

	public static byte[] getTileDescriptors(Connection conn, String schema, String table)
		throws SQLException, IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		getTileDescriptors(conn, schema, table, baos);
		return baos.toByteArray();
	}
	public static void getTileDescriptors(Connection conn, String schema, String table, OutputStream output)
		throws SQLException, IOException
	{
		// this function assumes there are no missing tile descriptors in the table.

		DataOutputStream data = new DataOutputStream(output);

		Statement stmt = null;
		ResultSet rs = null;
		String query = "";
		SQLException pg = null;
		try
		{
			if (conn.getMetaData().getDatabaseProductName().equalsIgnoreCase(SQLUtils.POSTGRESQL))
			{////////////////////////////////////////////////////////////////////////////////
				// BACKWARDS COMPATIBILITY POSTGRESQL HACK -- CLEAN UP THIS CODE
				try
				{
					// copy binary data to stream
					stmt = conn.createStatement();
					query = "SELECT ";
					String[] names = new String[]{
						SQLGeometryStreamDestination.MIN_IMPORTANCE.toLowerCase(), SQLGeometryStreamDestination.MAX_IMPORTANCE.toLowerCase(),
						SQLGeometryStreamDestination.X_MIN_BOUNDS.toLowerCase(), SQLGeometryStreamDestination.Y_MIN_BOUNDS.toLowerCase(),
						SQLGeometryStreamDestination.X_MAX_BOUNDS.toLowerCase(), SQLGeometryStreamDestination.Y_MAX_BOUNDS.toLowerCase(),
						SQLGeometryStreamDestination.TILE_ID.toLowerCase()
					};
					for (int i = 0; i < names.length; i++)
					{
						if (i > 0)
							query += ", ";
						query += SQLUtils.quoteSymbol(conn, names[i]);
					}
					query += " FROM " + SQLUtils.quoteSchemaTable(conn, schema, table);
					query += " ORDER BY " + SQLUtils.quoteSymbol(conn, SQLGeometryStreamDestination.TILE_ID.toLowerCase());
					rs = stmt.executeQuery(query);
					while (rs.next())
					{
						data.writeFloat(rs.getFloat(1));
						data.writeFloat(rs.getFloat(2));
						data.writeDouble(rs.getDouble(3));
						data.writeDouble(rs.getDouble(4));
						data.writeDouble(rs.getDouble(5));
						data.writeDouble(rs.getDouble(6));
					}
					return; // if this succeeds, we don't want to run the second query
				}
				catch (SQLException e)
				{
					pg = e; // will be printed below if second try fails
				}
				// END HACK
			}////////////////////////////////////////////////////////////////////////////////
			
			
			// copy binary data to stream
			stmt = conn.createStatement();
			query = "SELECT ";
			String[] names = new String[]{
					SQLGeometryStreamDestination.MIN_IMPORTANCE, SQLGeometryStreamDestination.MAX_IMPORTANCE,
					SQLGeometryStreamDestination.X_MIN_BOUNDS, SQLGeometryStreamDestination.Y_MIN_BOUNDS,
					SQLGeometryStreamDestination.X_MAX_BOUNDS, SQLGeometryStreamDestination.Y_MAX_BOUNDS,
					SQLGeometryStreamDestination.TILE_ID
			};
			for (int i = 0; i < names.length; i++)
			{
				if (i > 0)
					query += ", ";
				query += SQLUtils.quoteSymbol(conn, names[i]);
			}
			query += " FROM " + SQLUtils.quoteSchemaTable(conn, schema, table);
			query += " ORDER BY " + SQLUtils.quoteSymbol(conn, SQLGeometryStreamDestination.TILE_ID);
			rs = stmt.executeQuery(query);
			while (rs.next())
			{
				data.writeFloat(rs.getFloat(1));
				data.writeFloat(rs.getFloat(2));
				data.writeDouble(rs.getDouble(3));
				data.writeDouble(rs.getDouble(4));
				data.writeDouble(rs.getDouble(5));
				data.writeDouble(rs.getDouble(6));
			}
		}
		catch (SQLException e)
		{
			if (pg != null)
			{
				System.err.println("Attempted both upper and lower-case column names in queries, both failed.");
				pg.printStackTrace();
				throw pg;
			}
			e.printStackTrace();
			throw e; // don't suppress the exception!
		}
		finally
		{
			SQLUtils.cleanup(rs);
			SQLUtils.cleanup(stmt);
		}
	}
	
	public static byte[] getTiles(Connection conn, String schema, String table, List<Integer> tileIDs)
		throws SQLException, IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		getTiles(conn, schema, table, tileIDs, baos);
		byte[] bytes = baos.toByteArray();
		//System.out.println("\n"+table+" tiles "+tileIDs+"\n"+getHexString(bytes));
		return bytes;
	}
	public static void getTiles(Connection conn, String schema, String table, List<Integer> tileIDs, OutputStream output)
		throws SQLException, IOException
	{
		DataOutputStream data = new DataOutputStream(output);
	
		CallableStatement cstmt = null;
		ResultSet rs = null;
		SQLException pg = null;
		try
		{
			if (conn.getMetaData().getDatabaseProductName().equalsIgnoreCase(SQLUtils.POSTGRESQL))
			{////////////////////////////////////////////////////////////////////////////////
				// BACKWARDS COMPATIBILITY POSTGRESQL HACK -- CLEAN UP THIS CODE
				try
				{
					// loop through tileIDs, copying binary data to stream
					cstmt = conn.prepareCall(
							"SELECT " + SQLUtils.quoteSymbol(conn, SQLGeometryStreamDestination.TILE_DATA.toLowerCase()) +
							" FROM " + SQLUtils.quoteSchemaTable(conn, schema, table) +
							" WHERE " + SQLUtils.quoteSymbol(conn, SQLGeometryStreamDestination.TILE_ID.toLowerCase()) + " = ?"
					);
					for (int i = 0; i < tileIDs.size(); i++)
					{
						cstmt.setInt(1, tileIDs.get(i));
						rs = cstmt.executeQuery();
						while (rs.next())
							data.write(rs.getBytes(1));
						rs.close();
						rs = null;
					}
					return; // if this succeeds, we don't want to run the second query
				}
				catch (SQLException e)
				{
					pg = e; // will be printed below if second try fails
				}
				// END HACK
			}////////////////////////////////////////////////////////////////////////////////
			
			// loop through tileIDs, copying binary data to stream
			cstmt = conn.prepareCall(
					"SELECT " + SQLUtils.quoteSymbol(conn, SQLGeometryStreamDestination.TILE_DATA) +
					" FROM " + SQLUtils.quoteSchemaTable(conn, schema, table) +
					" WHERE " + SQLUtils.quoteSymbol(conn, SQLGeometryStreamDestination.TILE_ID) + " = ?"
			);
			for (int i = 0; i < tileIDs.size(); i++)
			{
				cstmt.setInt(1, tileIDs.get(i));
				rs = cstmt.executeQuery();
				while (rs.next())
					data.write(rs.getBytes(1));
				rs.close();
				rs = null;
			}
		}
		catch (SQLException e)
		{
			if (pg != null)
			{
				System.err.println("Attempted both upper and lower-case column names in queries, both failed.");
				pg.printStackTrace();
				throw pg;
			}
			e.printStackTrace();
			throw e; // don't suppress the exception!
		}
		finally
		{
			// close everything in reverse order
			SQLUtils.cleanup(rs);
			SQLUtils.cleanup(cstmt);
		}
	}
//	private static String getHexString(byte bytes[])
//	{
//		String hex = "0123456789ABCDEF";
//		StringBuffer buf = new StringBuffer();
//		buf.append(String.format("(%s bytes)", bytes.length));
//		for (int i = 0; i < bytes.length; i++)
//		{
//			buf.append(hex.charAt(((bytes[i] & 0xFF) / 16) % 16));
//			buf.append(hex.charAt((bytes[i] & 0xFF) % 16));
//			
//			// debug
//			//buf.append(bytes[i] & 0xFF);
//			//buf.append(" ");
//		}
//		return buf.toString();
//	}
}
