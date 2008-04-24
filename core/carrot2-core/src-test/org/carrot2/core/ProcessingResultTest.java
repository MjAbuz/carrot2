package org.carrot2.core;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.carrot2.core.test.assertions.Carrot2CoreAssertions.assertThat;

import java.io.*;
import java.util.List;
import java.util.Map;

import org.carrot2.core.attribute.AttributeNames;
import org.carrot2.core.test.TestDocumentFactory;
import org.carrot2.util.CloseableUtils;
import org.carrot2.util.CollectionUtils;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class ProcessingResultTest
{
    @Test
    public void testSerializationDeserialization() throws Exception
    {
        final List<Document> documents = TestDocumentFactory.DEFAULT.generate(5);
        final Map<String, Object> attributes = Maps.newHashMap();
        attributes.put(AttributeNames.DOCUMENTS, documents);

        final Document document = documents.get(0);
        document.addField("testString", "test");
        document.addField("testInteger", 10);
        document.addField("testDouble", 10.3);
        document.addField("testBoolean", true);

        final Cluster clusterA = new Cluster();
        clusterA.addPhrases("Label 1", "Label 2");
        clusterA.setAttribute(Cluster.SCORE, 1.0);
        clusterA.setAttribute("testString", "test");
        clusterA.setAttribute("testInteger", 10);
        clusterA.setAttribute("testDouble", 10.3);
        clusterA.setAttribute("testBoolean", true);

        final Cluster clusterAA = new Cluster();
        clusterAA.addPhrases("Label 3");
        clusterAA.addDocuments(documents.get(0), documents.get(1));
        clusterA.addSubclusters(clusterAA);

        final Cluster clusterB = new Cluster();
        clusterB.addPhrases("Label 4");
        clusterB.setAttribute(Cluster.SCORE, 0.55);
        clusterB.addDocuments(documents.get(1), documents.get(2));

        final List<Cluster> clusters = Lists.newArrayList(clusterA, clusterB);
        attributes.put(AttributeNames.CLUSTERS, clusters);

        final ProcessingResult sourceProcessingResult = new ProcessingResult(attributes);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        sourceProcessingResult.serialize(outputStream);
        CloseableUtils.close(outputStream);

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream
            .toByteArray());

        final ProcessingResult deserialized = ProcessingResult.deserialize(inputStream);

        assertNotNull(deserialized);
        assertNotNull(deserialized.getAttributes());
        assertThat(deserialized.getDocuments()).isEquivalentTo(documents);
        assertThat(deserialized.getClusters()).isEquivalentTo(clusters);
    }

    @Test
    public void testDocumentDeserializationFromLegacyXml() throws Exception
    {
        final String query = "apple computer";
        final String title = "Apple Computer, Inc.";
        final String snippet = "Macintosh hardware, software, and Internet tools.";
        final String url = "http:// www.apple.com/";

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<searchresult>\n");
        xml.append("<query>" + query + "</query>\n");
        xml.append("<document id=\"0\">");
        xml.append("<title>" + title + "</title>\n");
        xml.append("<snippet>" + snippet + "</snippet>\n");
        xml.append("<url>" + url + "</url>\n");
        xml.append("</document>\n");
        xml.append("</searchresult>\n");

        final StringReader reader = new StringReader(xml.toString());

        final ProcessingResult deserialized = ProcessingResult.deserialize(reader);

        assertNotNull(deserialized);
        assertNotNull(deserialized.getAttributes());

        Document deserializedDocument = CollectionUtils.getFirst(deserialized
            .getDocuments());
        assertEquals(title, deserializedDocument.getField(Document.TITLE));
        assertEquals(snippet, deserializedDocument.getField(Document.SUMMARY));
        assertEquals(url, deserializedDocument.getField(Document.CONTENT_URL));
    }

    @Test
    public void testClusterDeserializationFromLegacyXml() throws Exception
    {
        final String query = "apple computer";

        final String title = "Apple Computer, Inc.";
        final String snippet = "Macintosh hardware, software, and Internet tools.";
        final String url = "http:// www.apple.com/";

        final int documentCount = 3;

        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<searchresult>");
        xml.append("<query>" + query + "</query>\n");
        for (int i = 0; i < documentCount; i++)
        {
            xml.append("<document id=\"" + i + "\">");
            xml.append("<title>" + title + i + "</title>\n");
            xml.append("<snippet>" + snippet + i + "</snippet>\n");
            xml.append("<url>" + url + i + "</url>\n");
            xml.append("</document>\n");
        }
        xml.append("<group score=\"1.0\">");
        xml.append("<title>");
        xml.append("<phrase>Data Mining Techniques</phrase>");
        xml.append("<phrase>Lectures</phrase>");
        xml.append("</title>");
        xml.append("<group>");
        xml.append("<title>");
        xml.append("<phrase>Research</phrase>");
        xml.append("</title>");
        xml.append("<document refid=\"0\"/>");
        xml.append("<document refid=\"1\"/>");
        xml.append("</group>");
        xml.append("</group>");
        xml.append("<group score=\"0.55\">");
        xml.append("<title>");
        xml.append("<phrase>Software</phrase>");
        xml.append("</title>");
        xml.append("<document refid=\"1\"/>");
        xml.append("<document refid=\"2\"/>");
        xml.append("</group>");
        xml.append("</searchresult>\n");

        final StringReader reader = new StringReader(xml.toString());

        final ProcessingResult deserialized = ProcessingResult.deserialize(reader);

        assertNotNull(deserialized);
        assertNotNull(deserialized.getAttributes());

        // Check documents
        assertThat(deserialized.getDocuments()).hasSize(documentCount);
        int index = 0;
        final List<Document> documents = deserialized.getDocuments();
        for (Document document : documents)
        {
            assertEquals(title + index, document.getField(Document.TITLE));
            assertEquals(snippet + index, document.getField(Document.SUMMARY));
            assertEquals(url + index, document.getField(Document.CONTENT_URL));
            index++;
        }

        // Check clusters
        final List<Cluster> clusters = deserialized.getClusters();

        final Cluster clusterA = new Cluster();
        clusterA.addPhrases("Data Mining Techniques", "Lectures");
        clusterA.setAttribute(Cluster.SCORE, 1.0);

        final Cluster clusterAA = new Cluster();
        clusterAA.addPhrases("Research");
        clusterAA.addDocuments(documents.get(0), documents.get(1));
        clusterA.addSubclusters(clusterAA);

        final Cluster clusterB = new Cluster();
        clusterB.addPhrases("Software");
        clusterB.setAttribute(Cluster.SCORE, 0.55);
        clusterB.addDocuments(documents.get(1), documents.get(2));

        assertThat(clusters).isEquivalentTo(Lists.newArrayList(clusterA, clusterB));
    }
}
