//  Copyright 2006, Clark N. Hobbie
//
//  This file is part of the util library.
//
//  The util library is free software; you can redistribute it and/or modify it
//  under the terms of the Lesser GNU General Public License as published by
//  the Free Software Foundation; either version 2.1 of the License, or (at
//  your option) any later version.
//
//  The util library is distributed in the hope that it will be useful, but
//  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
//  or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public
//  License for more details.
//
//  You should have received a copy of the Lesser GNU General Public License
//  along with the util library; if not, write to the Free Software Foundation,
//  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
//
package com.lts.io.archive;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.lts.LTSException;
import com.lts.io.DirectoryScanner;
import com.lts.io.IOUtilities;
import com.lts.io.ImprovedFile;
import com.lts.io.ImprovedFile.FileException;
import com.lts.util.deepcopy.DeepCopier;
import com.lts.util.deepcopy.DeepCopyException;
import com.lts.util.deepcopy.DeepCopyUtil;


/**
 * An archive that caches its contents in a system of files and directories.
 * 
 * <H2>This is an abstract class</H2>
 * To create a concrete implementation, subclasses must implement the 
 * following methods:
 * 
 * <UL>
 * <LI/>extractArchive
 * <LI/>basicGet
 * <LI/>basicList
 * <LI/>writeArchiveFromTo
 * </UL>
 */
public abstract class AbstractTempDirectoryArchive
    implements Archive
{
    /**
     * Extract the contents of the archive to the directory passed to the 
     * method.
     */
    public abstract void extractArchive (File dir)
        throws LTSException, IOException;
    
    /**
     * Get a file from the archive file rather than the directory "cache."
     */
    public abstract InputStream basicGet (String name)
        throws LTSException, IOException;
    
    /**
     * Get a list of the files from the archive file rather than using the
     * directory cache.
     */
    public abstract List basicList()
        throws LTSException, IOException;
    
    /**
     * Create an archive file, overwriting any existing file with the same 
     * name, using the files from the specified directory.
     */
    public abstract void writeArchiveFromTo (File inputDir, File outputFile)
        throws LTSException, IOException;
    
    
    public abstract long getEntrySize (String entry)
    	throws LTSException;
    
    /**
     * A cached version of the files in the archive.
     */
    protected List myFileList;
    
    /**
     * The archive file that this object represents.
     */
    protected ImprovedFile myArchiveFile;
    
    /**
     * The directory where the archive stores its image.
     *
     * <P>
     * This directory is generated by calling java.io.File.createTempFile, 
     * so the name is somewhat random.
     */
    protected ImprovedFile myTempDir;
    
    /**
     * The time when the underlying archive file was last written.
     *
     * <P>
     * If the time of last change represented here is different from the time
     * of last change on the archive, then someone has modified the archive
     * out from under us.
     */
    protected Date myLastArchiveWrite;
    
    /**
     * Whether the archive has been extracted to the temp directory.
     *
     * <P>
     * If the value of this attribute is true, then the contents of the archvie
     * has been extracted to the directory specified by the TempDirectory 
     * property.
     */
    protected boolean myArchiveHasBeenExtracted;
    
    
    private boolean myCreateBackups;
    
    public boolean getCreateBackups()
    {
    	return myCreateBackups;
    }
    
    public void setCreateBackups(boolean createBackups)
    {
    	myCreateBackups = createBackups;
    }
    
    public boolean createBackups()
    {
    	return getCreateBackups();
    }
    
    
    public AbstractTempDirectoryArchive ()
    {}
    
    public AbstractTempDirectoryArchive (File f)
    {
    	initialize(f,null, true);
    }
    
    
    public void initialize (File file, File tempdir, boolean createBackups)
    {
    	setTempDir(tempdir);
        ImprovedFile ifile = IOUtilities.toImprovedFile(file);
        setArchiveFile(ifile);
        setCreateBackups(createBackups);
    }
    
    
    public void initialize (File f)
    {
    	initialize(f, null, true);
    }
    
    public void initialize (String s)
    {
    	File f = new File(s);
    	initialize(f,null, true);
    }
    
    public AbstractTempDirectoryArchive (String s)
    {
    	ImprovedFile ifile = new ImprovedFile(s);
    	initialize(ifile,null, true);
    }
    
    
    public void initialize (InputStream istream, ImprovedFile tempdir)
    	throws IOException
    {
		ImprovedFile ifile = ImprovedFile.createTempImprovedFile(
			"arc",
			"zip",
			tempdir
		);
    	
		ifile.copyFrom(istream);
		
		initialize(ifile, tempdir, true);
    }
    
    
    public ImprovedFile getArchiveFile ()
    {
        return myArchiveFile;
    }
    
    public void setArchiveFile (ImprovedFile f)
    {
        myArchiveFile = f;
    }
    
    public File getFile ()
    {
    	return myArchiveFile;
    }
    
    public List getFileList ()
        throws LTSException, IOException
    {
        if (null == myFileList)
            myFileList = createFileList();
        
        return myFileList;
    }
    
    public void setFileList (List l)
    {
        myFileList = l;
    }
    
    
    public boolean archiveHasBeenExtracted ()
    {
        return myArchiveHasBeenExtracted;
    }
    
    public void setArchiveHasBeenExtracted (boolean b)
    {
        myArchiveHasBeenExtracted = b;
    }
    
    
    public ImprovedFile getTempDir()
        throws LTSException
    {
        try
        {
            if (null == myTempDir)
            {
                myTempDir = ImprovedFile.createTempDirectory("zarchive", "zip");
                myTempDir.delete();
                if (!myTempDir.mkdirs())
                {
                    throw new LTSException (
                        "Could not create temporary directory for archive: "
                        + myTempDir
                    );
                }
            }

            return myTempDir;
        }
        catch (IOException e)
        {
            throw new LTSException (
                "Encountered error while trying to create temp directory, "
                + myTempDir,
                e
            );
        }
    }
                
    
    public void setTempDir(File f)
    {
    	if (null == f)
    		myTempDir = null;
    	else if (f instanceof ImprovedFile)
    	{
    		myTempDir = (ImprovedFile) f;
    	}
    	else 
    	{
    		ImprovedFile temp = new ImprovedFile(f);
    		
    		if (temp.exists() && !temp.isDirectory() && !temp.delete())
    		{
    			throw new IllegalArgumentException (
    				"The supplied temp directory, " 
    				+ f
    				+ ", is a file and cannot be removed."
    			);
    		}
    		
    		if (!temp.exists() && !temp.mkdirs())
    		{
    			throw new IllegalArgumentException (
    				"The supplied temp directory, "
    				+ f
    				+ ", does not exist and could not be created."
    			);
    		}
    		
    		myTempDir = temp;
    	}
    }
    
    public void copyFromTo (InputStream istream, OutputStream ostream)
        throws IOException
    {
        byte[] buf = new byte[8192];
        int count = 0;
            
        do{
            count = istream.read(buf);
            if (count > 0)
                ostream.write(buf, 0, count);
        } while (count > 0);
    }
    
    
    public void copyFromTo (File inputFile, File outputFile)
        throws FileNotFoundException, LTSException
    {
    	FileInputStream fis = null;
    	FileOutputStream fos = null;
    	
        try
        {
            fis = new FileInputStream(inputFile);
            fos = new FileOutputStream(outputFile);
            copyFromTo(fis, fos);
        }
        catch (IOException e)
        {
            throw new LTSException (
                "Error trying to copy file to staging directory.  "
                + "Input file: " + inputFile + ", "
                + "output file: " + outputFile,
                e
            );
        }
        finally
        {
        	if (null != fis)
        	{
        		try { fis.close(); } catch (IOException e) {}
        	}
        	
        	if (null != fos)
        	{
        		try { fos.close(); } catch (IOException e) {}
        	}
        }
    }
    
    public void extractArchive()
        throws LTSException, IOException
    {
        if (!archiveHasBeenExtracted())
            extractArchive(getTempDir());
        
        setArchiveHasBeenExtracted(true);
    }
    
    
    public void createParentDirectories (File f)
    {
        File parent = f.getParentFile();
        if (!parent.exists())
        {
            parent.mkdirs();
        }
    }
    
    
    public void add (String name, InputStream istream)
        throws LTSException, IOException
    {
        File outputFile = new File(getTempDir(), name);
        FileOutputStream fos = null;
    
        try
        {
            extractArchive();
            createParentDirectories(outputFile);
            
            fos = new FileOutputStream(outputFile);
            copyFromTo (istream, fos);
        }
        catch (FileNotFoundException e)
        {
            throw new LTSException (
                "Error trying to copy input stream to temp output file, "
                + outputFile,
                e
            );
        }
        finally
        {
        	IOUtilities.closeNoExceptions(istream);
        	IOUtilities.closeNoExceptions(fos);
        }
    }
    
    
    public void add (String name, File inputFile)
        throws LTSException, FileNotFoundException, IOException
    {
        extractArchive();
        File outputFile = new File(getTempDir(), name);
        createParentDirectories(outputFile);
        IOUtilities.copyFile(inputFile, outputFile);
    }
    
    
    public InputStream getInputStream (String name) 
    	throws LTSException, IOException
    {
    	return get(name);
    }
    
    public OutputStream getOutputStream (String name)
    	throws LTSException, IOException
    {
    	extractArchive();
    	File file = new File(getTempDir(), name);
    	createParentDirectories(file);
    	FileOutputStream fos = new FileOutputStream(file);
    	return fos;
    }
    
    public boolean remove (String name)
        throws LTSException, IOException
    {
        extractArchive();
        File f = new File(getTempDir(), name);
        return f.delete();
    }
    

    public List listFilesInDirectory (File dir)
        throws LTSException
    {
        DirectoryScanner scan = new DirectoryScanner();
        scan.setBasedir(dir);
        String[] includes = {"**"};
        scan.setIncludes(includes);
        scan.scan();
        
        String[] files = scan.getIncludedFiles();
        List l = new ArrayList();
        
        for (int i = 0; i < files.length; i++)
        {
            l.add(files[i]);
        }
        return l;
    }
        
    
    public List createFileList ()
        throws LTSException, IOException
    {
        if (archiveHasBeenExtracted())
            return listFilesInDirectory(getTempDir());
        else
        {
            List l = basicList();
            if (null == l)
                l = new ArrayList();
                
            setFileList(l);
            return getFileList();
        }
    }
    
    
    public List list ()
        throws LTSException, IOException
    {
        return getFileList();
    }
    
    
    public OutputStream createEntry (String name)
    	throws LTSException
    {
    	File outfile = null;
    	
		try 
		{
			extractArchive();
			outfile = new File(getTempDir(), name);
			File parent = outfile.getParentFile();
			
			if (!parent.isDirectory() && !parent.mkdirs())
			{
				throw new LTSException (
					"Error trying to create temp directories, "
					+ parent
					+ ", for entry "
					+ name
					+ ", in archive "
					+ getFile()
				);
			}
			
			FileOutputStream fos = new FileOutputStream(outfile);
			return fos;    	
		} 
		catch (IOException e) 
		{
			throw new LTSException (
				"Error trying to create temp file for entry "
				+ name
				+ ", to file "
				+ outfile
				+ ", for archive "
				+ getFile(),
				e 
			);
		}
    }
    
    public InputStream get (String name)
        throws LTSException, IOException
    {
        InputStream istream;
        
        if (!entryExists(name))
        	return null;
        
        if (!archiveHasBeenExtracted())
            istream = basicGet (name);
        else
        {
            File f = new File(getTempDir(), name);
            try
            {
                istream = new FileInputStream(f);
            }
            catch (FileNotFoundException e)
            {
                istream = null;
            }
        }
        
        return istream;
    }
    
    public ImprovedFile getTempFileForEntry (String entry)
    	throws LTSException
    {
		try 
		{
			if (!archiveHasBeenExtracted())
				extractArchive();
			
			ImprovedFile f = new ImprovedFile(getTempDir(), entry);
			return f;
		} 
		catch (IOException e) 
		{
			throw new LTSException (
				"Error trying to extract out archive to temporary "
				+ "location.  Archive: " + getFile()
				+ ", temp location: " + getTempDir(),
				e
			);
		}
    }
    
    
    public void rollback ()
        throws LTSException
    {
        if (archiveHasBeenExtracted())
        {
        	try
        	{
        		getTempDir().deleteDirectory(true);
        	}
        	catch (IOException e)
        	{
                throw new LTSException (
                    "Error trying to remove archive staging directory, "
                    + getTempDir(),
                    e
                );
            }       
        }
    }
    
    
    
    public void commitTo (File ofile)
    	throws LTSException
    {
    	ImprovedFile outfile;
    	
    	if (ofile instanceof ImprovedFile)
    		outfile = (ImprovedFile) ofile;
    	else
    		outfile = new ImprovedFile(ofile);
    	
    	
		try 
		{
			if (outfile.exists() && createBackups())
				outfile.backup(true);
		} 
		catch (FileException e)
		{
			String msg = 
				"Error trying to backup file, "
				+ outfile
				+ ".  Reason: " + e.getReason();
			throw new LTSException(msg, e);
		}
		
    		
		try 
		{
			if (archiveHasBeenExtracted())
			{
				writeArchiveFromTo(getTempDir(), outfile);
			}
			else 
			{
				getArchiveFile().copyTo(outfile);
			}
		} 
		catch (FileNotFoundException e) 
		{
			throw new LTSException (
				"Error trying to create output file, " + outfile,
				e
			);
		} 
		catch (IOException e) 
		{
			throw new LTSException (
				"Error trying to write data to output file" + outfile,
				e
			);
		}
    }
    
    public void commit (File outfile) throws LTSException, IOException
    {
		if (archiveHasBeenExtracted())
		{
			if (getArchiveFile().exists() && createBackups())
			{
				try
				{
					getArchiveFile().backup(true);
				}
				catch (Exception e)
				{
					throw new LTSException (
						"Error trying to backup archive file, "
						+ getArchiveFile(),
						e
					);
				}
			}
			writeArchiveFromTo(getTempDir(), outfile);
		}
    }
    
    public void commit () throws LTSException, IOException
	{
    	commit(getArchiveFile());
	}
    
    public Object loadObject (String name)
        throws LTSException, IOException
    {
        try
        {
            InputStream istream = get(name);
            ObjectInputStream ois = new ObjectInputStream(istream);
            Object o = ois.readObject();
            ois.close();
            return o;
        }
        catch (Exception e)
        {
            throw new LTSException (
                "Caught exception while trying to load object from archive "
                + "for entry, " + name,
                e
            );
        }
    }
    
    
    public void saveObject (String name, Object o)
        throws LTSException, IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(o);
        oos.close();
        
        ByteArrayInputStream bais = 
            new ByteArrayInputStream(baos.toByteArray());
        
        add(name, bais);
        bais.close();
    }
    
    public void saveProperties (String name, Properties p)
        throws LTSException, IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        p.store(baos, null);
        baos.close();
        
        byte[] buf = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(buf);
        add(name, bais);
        bais.close();
    }
    
    
    public Properties loadProperties (String name)
        throws LTSException, IOException
    {
        InputStream istream = null;
        Properties p = new Properties();
        
        try
        {
            istream = get(name);
            if (null == istream)
                return null;
                
            p.load(istream);
        }
        finally
        {
            if (null != istream)
                istream.close();
        }
        
        return p;
    }
    
	public void removeTempFiles()
		throws IOException
	{
		if (null != myTempDir && myTempDir.exists())
		{
			myTempDir.deleteDirectory(true);
		}
	}
	
	public void finalize()
	{
		try
		{
			removeTempFiles();
		}
		catch (IOException e)
		{
			e.printStackTrace();    
		}
	}
	
	
	public File getTempExtractedFile (String entry)
		throws LTSException
	{
		try 
		{
			extractArchive();
			
			File f = new File(getTempDir(), entry);
			return f;
		} 
		catch (IOException e) 
		{
			throw new LTSException (
				"Error extracting archive to temp directory",
				e
			);
		}
	}
	
	
	public String convertOneString (String s)
	{
		char[] ca = s.toCharArray();
		StringBuffer sb = new StringBuffer(ca.length);
		
		for (int i = 0; i < ca.length; i++)
		{
			if (ca[i] == '\\')
				sb.append ('/');
			else
				sb.append (ca[i]);
		}
		
		return sb.toString();
	}
	
	
	public String toString()
	{
		File file = getArchiveFile();
		if (null == file)
			return "";
		else
			return file.toString(); 
	}
	
	
	public void deepCopyData(Object ocopy, Map map, boolean copyTransients)
		throws DeepCopyException
	{
		AbstractTempDirectoryArchive copy = (AbstractTempDirectoryArchive) ocopy;
		copy.myArchiveFile = (ImprovedFile) DeepCopyUtil.continueDeepCopy(this.myArchiveFile, map, copyTransients);
		copy.myArchiveHasBeenExtracted = this.myArchiveHasBeenExtracted;
		copy.myFileList = DeepCopyUtil.copyList(this.myFileList, map, copyTransients);
		copy.myLastArchiveWrite = (Date) DeepCopyUtil.continueDeepCopy(this.myLastArchiveWrite, map, copyTransients);
		copy.myTempDir = (ImprovedFile) DeepCopyUtil.continueDeepCopy(this.myTempDir, map, copyTransients);
	}

	public DeepCopier continueDeepCopy(Map map, boolean copyTransients) throws DeepCopyException
	{
		return (DeepCopier) DeepCopyUtil.continueDeepCopy(this, map, copyTransients);
	}

	public Object deepCopy() throws DeepCopyException
	{
		return DeepCopyUtil.deepCopy(this, false);
	}

	public Object deepCopy(boolean copyTransients) throws DeepCopyException
	{
		return DeepCopyUtil.deepCopy(this, copyTransients);
	}

}
