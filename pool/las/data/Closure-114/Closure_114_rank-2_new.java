package org.apache.maven.repository.converter;

/*
 * Copyright 2005-2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.metadata.ArtifactRepositoryMetadata;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.SnapshotArtifactRepositoryMetadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer;
import org.apache.maven.model.converter.ArtifactPomRewriter;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Implementation of repository conversion class.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @plexus.component role="org.apache.maven.repository.converter.RepositoryConverter" role-hint="default"
 */
public class DefaultRepositoryConverter
    implements RepositoryConverter
{
    /**
     * @plexus.requirement
     */
    private ArtifactFactory artifactFactory;

    /**
     * @plexus.requirement
     */
    private ArtifactPomRewriter rewriter;

    /**
     * @plexus.configuration
     */
    private boolean force;

    /**
     * @plexus.configuration
     */
    private boolean dryrun;

    public void convert( Artifact artifact, ArtifactRepository targetRepository )
        throws RepositoryConversionException
    {
        copyArtifact( artifact, targetRepository );

        copyPom( artifact, targetRepository );

        Metadata metadata = createBaseMetadata( artifact );
        Versioning versioning = new Versioning();
        versioning.addVersion( artifact.getBaseVersion() );
        metadata.setVersioning( versioning );
        updateMetadata( new ArtifactRepositoryMetadata( artifact ), targetRepository, metadata );

        metadata = createBaseMetadata( artifact );
        metadata.setVersion( artifact.getBaseVersion() );
        versioning = new Versioning();

        Matcher matcher = Artifact.VERSION_FILE_PATTERN.matcher( artifact.getVersion() );
        if ( matcher.matches() )
        {
            Snapshot snapshot = new Snapshot();
            snapshot.setBuildNumber( Integer.valueOf( matcher.group( 3 ) ).intValue() );
            snapshot.setTimestamp( matcher.group( 2 ) );
            versioning.setSnapshot( snapshot );
        }

        // TODO: merge latest/release/snapshot from source instead
        metadata.setVersioning( versioning );
        updateMetadata( new SnapshotArtifactRepositoryMetadata( artifact ), targetRepository, metadata );
    }

    private static Metadata createBaseMetadata( Artifact artifact )
    {
        Metadata metadata = new Metadata();
        metadata.setArtifactId( artifact.getArtifactId() );
        metadata.setGroupId( artifact.getGroupId() );
        return metadata;
    }

    private void updateMetadata( ArtifactMetadata artifactMetadata, ArtifactRepository targetRepository,
                                 Metadata newMetadata )
        throws RepositoryConversionException
    {
        File file = new File( targetRepository.getBasedir(),
                              targetRepository.pathOfRemoteRepositoryMetadata( artifactMetadata ) );

        Metadata metadata;
        boolean changed;

        if ( file.exists() )
        {
            MetadataXpp3Reader reader = new MetadataXpp3Reader();
            FileReader fileReader = null;
            try
            {
                fileReader = new FileReader( file );
                metadata = reader.read( fileReader );
            }
            catch ( IOException e )
            {
                throw new RepositoryConversionException( "Error reading target metadata", e );
            }
            catch ( XmlPullParserException e )
            {
                throw new RepositoryConversionException( "Error reading target metadata", e );
            }
            finally
            {
                IOUtil.close( fileReader );
            }
            changed = metadata.merge( newMetadata );
        }
        else
        {
            changed = true;
            metadata = newMetadata;
        }

        if ( changed )
        {
            Writer writer = null;
            try
            {
                file.getParentFile().mkdirs();
                writer = new FileWriter( file );

                MetadataXpp3Writer mappingWriter = new MetadataXpp3Writer();

                mappingWriter.write( writer, metadata );
            }
            catch ( IOException e )
            {
                throw new RepositoryConversionException( "Error writing target metadata", e );
            }
            finally
            {
                IOUtil.close( writer );
            }
        }
    }

    private void copyPom( Artifact artifact, ArtifactRepository targetRepository )
        throws RepositoryConversionException
    {
        Artifact pom = artifactFactory.createProjectArtifact( artifact.getGroupId(), artifact.getArtifactId(),
                                                              artifact.getVersion() );
        pom.setBaseVersion( artifact.getBaseVersion() );
        ArtifactRepository repository = artifact.getRepository();
        File file = new File( repository.getBasedir(), repository.pathOf( pom ) );

        if ( file.exists() )
        {
            // TODO: utility methods in the model converter
            File targetFile = new File( targetRepository.getBasedir(), targetRepository.pathOf( pom ) );

            String contents;
            try
            {
                contents = FileUtils.fileRead( file );
            }
            catch ( IOException e )
            {
                throw new RepositoryConversionException( "Unable to read source POM: " + e.getMessage(), e );
            }

            if ( contents.indexOf( "modelVersion" ) >= 0 )
            {
                // v4 POM
                try
                {
                    boolean matching = false;
                    if ( !force && targetFile.exists() )
                    {
                        String targetContents = FileUtils.fileRead( targetFile );
                        matching = targetContents.equals( contents );
                    }
                    if ( force || !matching )
                    {
                        if ( !dryrun )
                        {
                            targetFile.getParentFile().mkdirs();
                            FileUtils.fileWrite( targetFile.getAbsolutePath(), contents );
                        }
                    }
                }
                catch ( IOException e )
                {
                    throw new RepositoryConversionException( "Unable to write target POM: " + e.getMessage(), e );
                }
            }
            else
            {
                // v3 POM
                StringReader stringReader = new StringReader( contents );
                Writer fileWriter = null;
                try
                {
                    fileWriter = new FileWriter( targetFile );

                    // TODO: this api could be improved - is it worth having or go back to modelConverter?
                    rewriter.rewrite( stringReader, fileWriter, false, artifact.getGroupId(), artifact.getArtifactId(),
                                      artifact.getVersion(), artifact.getType() );

                    IOUtil.close( fileWriter );
                }
                catch ( Exception e )
                {
                    if ( fileWriter != null )
                    {
                        IOUtil.close( fileWriter );
                        targetFile.delete();
                    }
                    throw new RepositoryConversionException( "Unable to write converted POM", e );
                }
            }
        }
    }

    private void copyArtifact( Artifact artifact, ArtifactRepository targetRepository )
        throws RepositoryConversionException
    {
        File sourceFile = artifact.getFile();

        File targetFile = new File( targetRepository.getBasedir(), targetRepository.pathOf( artifact ) );

        try
        {
            boolean matching = false;
            if ( !force && targetFile.exists() )
            {
                matching = FileUtils.contentEquals( sourceFile, targetFile );
            }
            if ( force || !matching )
            {
                if ( !dryrun )
                {
                    FileUtils.copyFile( sourceFile, targetFile );
                }
            }
        }
        catch ( IOException e )
        {
            throw new RepositoryConversionException( "Error copying artifact", e );
        }
    }

    public void convert( List artifacts, ArtifactRepository targetRepository )
        throws RepositoryConversionException
    {
        for ( Iterator i = artifacts.iterator(); i.hasNext(); )
        {
            Artifact artifact = (Artifact) i.next();
            convert( artifact, targetRepository );
        }
    }
}
