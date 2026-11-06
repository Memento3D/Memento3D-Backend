package com.memento3d.customization.application.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.io.*;
import java.nio.*;
import java.util.*;

/** Lossless static GLB composition: rebase indices/buffers, retain textures and mesh bytes.
 * Unsupported graph features fail explicitly, never silently disappear from a print file. */
@Component @RequiredArgsConstructor
public class GlbDesignAssembler {
    private final ObjectMapper mapper;
    public record Part(byte[] bytes,List<Double> matrix) {}
    private static final List<String> TABLES=List.of("bufferViews","accessors","images","samplers","textures","materials","meshes","nodes");
    public byte[] assemble(List<Part> parts,int heightCm) throws IOException {
        if(parts.isEmpty()||heightCm<8||heightCm>20)throw new IOException("Invalid composition");
        if(parts.size()>8 || parts.stream().mapToLong(p->p.bytes().length).sum()>192L*1024*1024)throw new IOException("Composition exceeds memory budget");
        ObjectNode out=mapper.createObjectNode();out.putObject("asset").put("version","2.0").put("generator","Memento3D frozen-design-v1");
        TABLES.forEach(t->out.putArray(t));ArrayNode roots=mapper.createArrayNode();
        double[] actualBounds={Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY};
        Set<String> used=new TreeSet<>(),required=new TreeSet<>();ByteArrayOutputStream binary=new ByteArrayOutputStream();
        for(Part part:parts) {
            ByteBuffer b=ByteBuffer.wrap(part.bytes()).order(ByteOrder.LITTLE_ENDIAN);
            if(b.remaining()<20||b.getInt()!=0x46546c67||b.getInt()!=2||b.getInt()!=part.bytes().length)throw new IOException("Invalid GLB header");
            int length=b.getInt();if(b.getInt()!=0x4e4f534a||length<2||length>b.remaining())throw new IOException("Invalid GLB JSON");
            byte[] json=new byte[length];b.get(json);ObjectNode doc=(ObjectNode)mapper.readTree(json);
            if(b.remaining()<8)throw new IOException("Missing GLB buffer");
            int binLength=b.getInt();if(b.getInt()!=0x004e4942||binLength!=b.remaining())throw new IOException("Invalid GLB buffer");
            byte[] bin=new byte[binLength];b.get(bin);
            if(doc.path("buffers").size()!=1||doc.path("buffers").get(0).has("uri")||doc.path("buffers").get(0).path("byteLength").asInt()>bin.length)
                throw new IOException("External or invalid buffers are unsupported");
            for(String feature:List.of("skins","animations","cameras"))if(doc.path(feature).size()>0)throw new IOException("Unsupported animated/camera scene");
            for(String extKey:List.of("extensionsUsed","extensionsRequired"))for(JsonNode extension:doc.path(extKey)) {
                String name=extension.asText();if(!Set.of("KHR_materials_unlit","KHR_texture_transform").contains(name))throw new IOException("Unsupported GLB extension: "+name);
                (extKey.equals("extensionsUsed")?used:required).add(name);
            }
            Map<String,Integer> offset=new HashMap<>();for(String table:TABLES)offset.put(table,out.withArray(table).size());
            int byteOffset=binary.size();binary.write(bin);while(binary.size()%4!=0)binary.write(0);
            for(String table:TABLES)for(JsonNode source:doc.path(table)) {
                ObjectNode node=source.deepCopy();
                switch(table) {
                    case "bufferViews" -> {if(node.path("buffer").asInt()!=0||node.has("extensions"))throw new IOException("Unsupported buffer view");node.put("byteOffset",node.path("byteOffset").asInt()+byteOffset);}
                    case "accessors" -> {ref(node,"bufferView",offset.get("bufferViews"));if(node.has("sparse")){ref((ObjectNode)node.path("sparse").path("indices"),"bufferView",offset.get("bufferViews"));ref((ObjectNode)node.path("sparse").path("values"),"bufferView",offset.get("bufferViews"));}}
                    case "images" -> {if(node.has("uri"))throw new IOException("External image is unsupported");ref(node,"bufferView",offset.get("bufferViews"));}
                    case "textures" -> {ref(node,"source",offset.get("images"));ref(node,"sampler",offset.get("samplers"));}
                    case "materials" -> textures(node,offset.get("textures"));
                    case "meshes" -> {
                        if(node.has("weights"))throw new IOException("Morph weights are unsupported");
                        for(JsonNode primitive:node.path("primitives")) {
                            if(primitive.has("extensions")||primitive.path("targets").size()>0||primitive.path("mode").asInt(4)!=4)throw new IOException("Unsupported mesh primitive");
                            ObjectNode p=(ObjectNode)primitive;ref(p,"indices",offset.get("accessors"));ref(p,"material",offset.get("materials"));
                            ObjectNode attributes=(ObjectNode)p.path("attributes");var fields=attributes.fieldNames();List<String> names=new ArrayList<>();fields.forEachRemaining(names::add);
                            for(String name:names)attributes.put(name,attributes.get(name).intValue()+offset.get("accessors"));
                        }
                    }
                    case "nodes" -> {
                        if(node.has("skin")||node.has("camera")||node.has("extensions")||node.has("weights"))throw new IOException("Unsupported node");
                        ref(node,"mesh",offset.get("meshes"));if(node.has("children")) {ArrayNode children=mapper.createArrayNode();for(JsonNode child:node.path("children"))children.add(child.intValue()+offset.get("nodes"));node.set("children",children);}
                    }
                }
                out.withArray(table).add(node);
            }
            JsonNode selected=doc.path("scenes").path(doc.path("scene").asInt(0)).path("nodes");
            if(!selected.isArray()||selected.isEmpty())throw new IOException("Missing active GLB scene");
            double[] placementMatrix=part.matrix()==null?identity():part.matrix().stream().mapToDouble(Double::doubleValue).toArray();
            if(placementMatrix.length!=16)throw new IOException("Invalid placement");
            for(JsonNode root:selected)bounds(doc,root.intValue(),placementMatrix,actualBounds,new HashSet<>(),bin);
            ObjectNode placement=mapper.createObjectNode();ArrayNode children=placement.putArray("children");
            for(JsonNode root:selected)children.add(root.intValue()+offset.get("nodes"));
            if(part.matrix()!=null){if(part.matrix().size()!=16||part.matrix().stream().anyMatch(v->v==null||!Double.isFinite(v)))throw new IOException("Invalid placement");placement.set("matrix",mapper.valueToTree(part.matrix()));}
            roots.add(out.withArray("nodes").size());out.withArray("nodes").add(placement);
        }
        // glTF uses metres. Apply one uniform scale to the WHOLE assembled figure.
        double height=actualBounds[1]-actualBounds[0];if(!Double.isFinite(height)||height<=0)throw new IOException("Invalid model bounds");
        ObjectNode scale=mapper.createObjectNode();scale.set("children",roots);double factor=heightCm/100.0/height;
        scale.putArray("scale").add(factor).add(factor).add(factor);
        int finalRoot=out.withArray("nodes").size();out.withArray("nodes").add(scale);
        out.putArray("scenes").addObject().putArray("nodes").add(finalRoot);out.put("scene",0);
        out.putArray("buffers").addObject().put("byteLength",binary.size());
        if(!used.isEmpty())out.set("extensionsUsed",mapper.valueToTree(used));if(!required.isEmpty())out.set("extensionsRequired",mapper.valueToTree(required));
        byte[] json=mapper.writeValueAsBytes(out);int padded=(json.length+3)/4*4;
        ByteBuffer result=ByteBuffer.allocate(12+8+padded+8+binary.size()).order(ByteOrder.LITTLE_ENDIAN);
        result.putInt(0x46546c67).putInt(2).putInt(result.capacity()).putInt(padded).putInt(0x4e4f534a).put(json);
        while(result.position()<20+padded)result.put((byte)0x20);
        result.putInt(binary.size()).putInt(0x004e4942).put(binary.toByteArray());return result.array();
    }
    private void ref(ObjectNode node,String key,int offset){if(node.has(key))node.put(key,node.get(key).intValue()+offset);}
    private void textures(JsonNode node,int offset) {
        if(node.isObject()){var fields=node.fields();while(fields.hasNext()){var field=fields.next();
            if(field.getKey().endsWith("Texture")&&field.getValue().has("index"))ref((ObjectNode)field.getValue(),"index",offset);
            else textures(field.getValue(),offset);
        }}else if(node.isArray())for(JsonNode child:node)textures(child,offset);
    }
    private static double[] identity(){return new double[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};}
    private double[] transform(JsonNode node) throws IOException {
        if(node.has("matrix")){double[] m=new double[16];if(node.path("matrix").size()!=16)throw new IOException("Invalid matrix");for(int i=0;i<16;i++)m[i]=node.path("matrix").get(i).asDouble();return m;}
        double x=value(node,"rotation",0,0),y=value(node,"rotation",1,0),z=value(node,"rotation",2,0),w=value(node,"rotation",3,1);
        double sx=value(node,"scale",0,1),sy=value(node,"scale",1,1),sz=value(node,"scale",2,1);
        return new double[]{(1-2*y*y-2*z*z)*sx,(2*x*y+2*z*w)*sx,(2*x*z-2*y*w)*sx,0,
            (2*x*y-2*z*w)*sy,(1-2*x*x-2*z*z)*sy,(2*y*z+2*x*w)*sy,0,
            (2*x*z+2*y*w)*sz,(2*y*z-2*x*w)*sz,(1-2*x*x-2*y*y)*sz,0,
            value(node,"translation",0,0),value(node,"translation",1,0),value(node,"translation",2,0),1};
    }
    private double value(JsonNode n,String key,int i,double fallback){return n.path(key).path(i).asDouble(fallback);}
    private double[] multiply(double[] a,double[] b){double[] r=new double[16];for(int c=0;c<4;c++)for(int row=0;row<4;row++)for(int k=0;k<4;k++)r[c*4+row]+=a[k*4+row]*b[c*4+k];return r;}
    private void bounds(ObjectNode doc,int id,double[] parent,double[] bounds,Set<Integer> ancestors,byte[] binary) throws IOException {
        if(!ancestors.add(id)||id<0||id>=doc.withArray("nodes").size())throw new IOException("Invalid scene graph");
        JsonNode node=doc.withArray("nodes").get(id);double[] world=multiply(parent,transform(node));
        if(node.has("mesh"))for(JsonNode primitive:doc.withArray("meshes").path(node.path("mesh").intValue()).path("primitives")) {
            JsonNode accessor=doc.withArray("accessors").path(primitive.path("attributes").path("POSITION").asInt(-1));
            if(accessor.path("componentType").asInt()!=5126||!"VEC3".equals(accessor.path("type").asText())||accessor.has("sparse"))throw new IOException("Unsupported POSITION accessor");
            JsonNode view=doc.withArray("bufferViews").path(accessor.path("bufferView").asInt(-1));
            int offset=view.path("byteOffset").asInt()+accessor.path("byteOffset").asInt(),stride=view.path("byteStride").asInt(12),count=accessor.path("count").asInt();
            if(offset<0||stride<12||count<=0||(long)offset+(long)(count-1)*stride+12>binary.length)throw new IOException("Invalid POSITION buffer");
            var positions=ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);
            for(int index=0;index<count;index++) {
                int p=offset+index*stride;double x=positions.getFloat(p),y=positions.getFloat(p+4),z=positions.getFloat(p+8);
                if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IOException("Invalid vertex");
                double actual=world[1]*x+world[5]*y+world[9]*z+world[13];bounds[0]=Math.min(bounds[0],actual);bounds[1]=Math.max(bounds[1],actual);
            }
        }
        for(JsonNode child:node.path("children"))bounds(doc,child.intValue(),world,bounds,ancestors,binary);ancestors.remove(id);
    }
}
