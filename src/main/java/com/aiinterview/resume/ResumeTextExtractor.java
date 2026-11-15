package com.aiinterview.resume;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Conservative, source-backed extraction. Never generates missing personal experience. */
public final class ResumeTextExtractor {
    private static final Pattern DATE=Pattern.compile("(?:19|20)\\d{2}(?:[./年-]\\d{1,2}月?)?\\s*(?:[-—–~～至到]|\\s+-\\s+)\\s*(?:(?:19|20)\\d{2}(?:[./年-]\\d{1,2}月?)?|至今|现在|present|now)",Pattern.CASE_INSENSITIVE);
    private static final Pattern SCHOOL=Pattern.compile("[\\p{IsHan}A-Za-z·]+(?:大学|学院|学校)|(?:[A-Za-z]+\\s+){0,5}(?:University|College)(?:\\s+of\\s+[A-Za-z]+)?",Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPANY=Pattern.compile("[\\p{IsHan}A-Za-z0-9·]+(?:有限公司|有限责任公司|股份公司|公司|研究院|工作室)|(?:[A-Za-z]+\\s+){0,4}(?:Inc\\.?|Ltd\\.?|Corporation)",Pattern.CASE_INSENSITIVE);
    private static final Pattern DEGREE=Pattern.compile("博士研究生|硕士研究生|本科|学士|硕士|博士|大专|专科|Bachelor(?:'s)?|Master(?:'s)?|PhD",Pattern.CASE_INSENSITIVE);
    private static final Pattern ROLE=Pattern.compile("[\\p{IsHan}A-Za-z+#.]+(?:工程师|开发实习生|实习生|开发助理|研究助理|开发人员|设计师|产品经理)|(?:[A-Za-z]+\\s+){0,3}(?:Engineer|Developer|Intern)",Pattern.CASE_INSENSITIVE);
    private static final Set<String> MAJORS=Set.of("软件工程","计算机科学与技术","计算机技术","计算机科学","网络工程","信息安全","人工智能","数据科学与大数据技术","电子信息工程","自动化","数学与应用数学","通信工程","信息管理与信息系统");
    private final List<ResumeParser.Evidence> evidence=new ArrayList<>();
    private final List<String> warnings=new ArrayList<>();
    private ResumeTextExtractor() {}
    public static ResumeParser.ParsedResume extract(String text) { return new ResumeTextExtractor().run(text); }
    private ResumeParser.ParsedResume run(String original) {
        Map<String,List<String>> sections=new LinkedHashMap<>();String section="other";
        for(String raw:original.split("\\R")) {
            String line=Normalizer.normalize(raw,Normalizer.Form.NFKC).replace('\u00a0',' ').strip();
            if(line.isBlank())continue;
            String heading=heading(line);
            if(heading!=null){section=heading;sections.computeIfAbsent(section,k->new ArrayList<>());continue;}
            sections.computeIfAbsent(section,k->new ArrayList<>()).add(line);
        }
        var education=experiences(sections.getOrDefault("education",List.of()),"education");
        var internships=experiences(sections.getOrDefault("internships",List.of()),"internships");
        var projects=projects(sections.getOrDefault("projects",List.of()));
        List<String> skills=new ArrayList<>();
        for(String line:sections.getOrDefault("skills",List.of())) {
            for(String token:line.replaceFirst("^(?:技能|技术栈|Skills)\\s*[:：]\\s*","").split("[,，、;；|]")) {
                String value=token.strip();if(!value.isBlank()&&value.length()<=128&&!skills.contains(value)&&skills.size()<100){skills.add(value);proof("skills["+(skills.size()-1)+"]",line,"REVIEW");}
            }
        }
        if(education.isEmpty())warnings.add("未识别到教育经历，请核对章节标题或手动补充。");
        if(internships.isEmpty())warnings.add("未识别到实习经历；没有相关经历可留空。");
        if(projects.isEmpty())warnings.add("未识别到项目，请手动补充至少一项真实项目后确认。");
        if(!sections.getOrDefault("other",List.of()).isEmpty())warnings.add("部分文字未归入已知章节，原文已保留。");
        warnings.add("字段来自规则提取，仍需本人核对；未明确标注的项目职责与成果不会自动推断。");
        return new ResumeParser.ParsedResume(original,new ResumePayload(education,skills,projects,internships),"sections-v2",List.copyOf(evidence),List.copyOf(warnings));
    }
    private static String heading(String line) {
        String key=line.replaceFirst("^\\s*[#★☆●•▪◆◇【\\[]+","")
                .replaceFirst("^(?:第?[一二三四五六七八九十]+[章节、.．)）:：]|\\d+[、.．)）:：])\\s*","")
                .replaceAll("[\\s\\p{Z}:：\\[\\]【】#★☆●•▪◆◇]","").toLowerCase(Locale.ROOT);
        return switch(key) {
            case "教育经历","教育背景","教育信息","学习经历","education","educationbackground" -> "education";
            case "专业技能","技能","技能清单","技术技能","技术能力","skills","technicalskills" -> "skills";
            case "项目经历","项目经验","项目实践","科研项目","projects","projectexperience" -> "projects";
            case "实习经历","实习经验","工作经历","工作经验","职业经历","internship","internships","workexperience","experience" -> "internships";
            case "个人信息","基本信息","自我评价","个人总结","荣誉奖项","获奖情况","证书","校园经历","社团经历","其他经历","兴趣爱好","自我介绍","summary","awards","certificates","interests" -> "other";
            default -> null;
        };
    }
    private List<List<String>> blocks(List<String> lines,String kind) {
        List<List<String>> blocks=new ArrayList<>();List<String> current=new ArrayList<>();
        boolean hasAnchor=false;
        for(String line:lines) {
            boolean anchor=isAnchor(line,kind);
            boolean dated=DATE.matcher(line).find();
            boolean starts=anchor&&hasAnchor;
            // A date-only line can precede an organization. Keep those two header lines together.
            if(dated&&!current.isEmpty()&&current.stream().anyMatch(s->DATE.matcher(s).find())&&hasAnchor)starts=true;
            if(starts){blocks.add(current);current=new ArrayList<>();hasAnchor=false;}
            current.add(line);hasAnchor|=anchor;
        }
        if(!current.isEmpty())blocks.add(current);
        if(blocks.size()>30){warnings.add(kind+" 识别到超过30项，仅生成前30项，全部文字保留在原文。");return blocks.subList(0,30);}
        return blocks;
    }
    private static boolean isAnchor(String line,String kind) {
        if(line.length()>250)return false;
        // A standalone date belongs to a header; its year is not a numbered project title.
        if(kind.equals("projects")&&DATE.matcher(line).find()&&DATE.matcher(line).replaceAll("").replaceAll("[\\s|｜:：-]","").isBlank())return false;
        if(kind.equals("projects"))return line.matches("(?i)^(?:项目(?:名称|名)|Project(?:\\s*Name)?)\\s*[:：].+")
                ||line.matches("^(?:项目[一二三四五六七八九十0-9]+|[一二三四五六七八九十0-9]+[、.)）])\\s*[:：]?.+")
                ||DATE.matcher(line).find()&&!DATE.matcher(line).replaceAll("").replaceAll("[\\s|｜:：-]","").isBlank()&&!line.matches("^(?:[-•●▪]\\s*)?(?:负责|参与|完成|实现|优化|职责|成果).*");
        if(line.matches("^(?:学校|院校|公司|单位|实习单位)\\s*[:：].+"))return true;
        Pattern p=kind.equals("education")?SCHOOL:COMPANY;
        return p.matcher(line).find()&&!line.matches("^(?:[-•●▪]\\s*)?(?:负责|参与|完成|实现|优化|描述|说明|主修).*");
    }
    private List<ResumePayload.Experience> experiences(List<String> lines,String kind) {
        List<ResumePayload.Experience> result=new ArrayList<>();int index=0;
        for(List<String> block:blocks(lines,kind)) {
            String path=kind+"["+index+++"]";String organization="",title="",period="";
            List<String> remaining=new ArrayList<>();
            for(String line:block) {
                String rest=line;
                String previousTitle=title;
                Matcher time=DATE.matcher(rest);
                if(period.isEmpty()&&time.find()){period=time.group();proof(path+".period",line,"MATCHED");rest=rest.replace(period,"");}
                String explicit=label(rest,kind.equals("education")?"学校|院校":"公司|单位|实习单位");
                if(organization.isEmpty()&&explicit!=null){organization=explicit;proof(path+".organization",line,"MATCHED");continue;}
                Matcher org=(kind.equals("education")?SCHOOL:COMPANY).matcher(rest);
                if(organization.isEmpty()&&org.find()){organization=org.group();proof(path+".organization",line,"REVIEW");rest=rest.replace(organization,"");}
                explicit=label(rest,kind.equals("education")?"专业(?:[/／]学位)?|专业名称|学位|学历":"岗位|职位|职务|担任角色");
                if(explicit!=null){title=join(title,explicit," / ");proof(path+".title",line,"MATCHED");continue;}
                if(kind.equals("education")) {
                    for(String major:MAJORS.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList())if(rest.contains(major)){title=join(title,major," / ");rest=rest.replace(major,"");break;}
                    Matcher degree=DEGREE.matcher(rest);if(degree.find()){title=join(title,degree.group()," / ");rest=rest.replace(degree.group(),"");}
                }else{Matcher role=ROLE.matcher(rest);if(title.isEmpty()&&role.find()){title=role.group();rest=rest.replace(title,"");}}
                if(!title.equals(previousTitle))proof(path+".title",line,"REVIEW");
                rest=rest.replaceAll("^[\\s|｜·,，;；:：-]+|[\\s|｜·,，;；:：-]+$","");
                if(!rest.isBlank())remaining.add(rest);
            }
            if(organization.isBlank())warnings.add(path+" 的机构名称未能确定，请手动核对。");
            String description=bounded(String.join("\n",remaining),5000,path+".description");
            if(!description.isBlank())proof(path+".description",String.join("\n",block),"REVIEW");
            result.add(new ResumePayload.Experience(bounded(organization,256,path),bounded(title,256,path),bounded(period,128,path),description));
        }
        return result;
    }
    private List<ResumePayload.Project> projects(List<String> lines) {
        List<ResumePayload.Project> result=new ArrayList<>();int index=0;
        for(List<String> block:blocks(lines,"projects")) {
            String path="projects["+index+++"]";Map<String,String> fields=new HashMap<>();List<String> techs=new ArrayList<>();String active=null;boolean uncertain=false;
            for(int i=0;i<block.size();i++) {
                String line=block.get(i);String clean=line.replaceFirst("^[-•●▪]\\s*","");
                Matcher labeled=Pattern.compile("^([^:：]{1,20})\\s*[:：]\\s*(.*)$").matcher(clean);
                String key=null,value=null;
                if(labeled.matches()){key=projectKey(labeled.group(1));value=labeled.group(2).strip();}
                if(key!=null){active=key;if(key.equals("unassigned")){uncertain=true;continue;}if(key.equals("techStack")){addTech(techs,value);proof(path+".techStack",line,"MATCHED");}else{fields.merge(key,value,(a,b)->a+"\n"+b);proof(path+"."+key,line,"MATCHED");}continue;}
                if(i==0||!fields.containsKey("name")&&active==null){
                    String name=DATE.matcher(clean).replaceAll("").replaceFirst("^(?:项目[一二三四五六七八九十0-9]+|[一二三四五六七八九十0-9]+[、.)）])\\s*[:：]?\\s*","").replaceAll("^[\\s|｜:：-]+|[\\s|｜:：-]+$","");
                    if(!name.isBlank()){fields.put("name",name);proof(path+".name",line,"REVIEW");}
                    continue;
                }
                if(active!=null&&!active.equals("name")&&!active.equals("unassigned")){if(active.equals("techStack")){addTech(techs,clean);proof(path+".techStack",line,"REVIEW");}else{fields.merge(active,clean,(a,b)->a+"\n"+b);proof(path+"."+active,line,"REVIEW");}}
                else uncertain=true;
            }
            if(uncertain)warnings.add(path+" 有未明确标注用途的描述，请对照原文手动归入目标、本人职责或成果。");
            if(fields.getOrDefault("responsibility","").isBlank())warnings.add(path+" 未找到明确的本人职责，确认前请补充。");
            result.add(new ResumePayload.Project(bounded(fields.getOrDefault("name",""),256,path+".name"),value(fields,"goal",path),value(fields,"responsibility",path),techs,value(fields,"difficulty",path),value(fields,"solution",path),value(fields,"result",path),value(fields,"improvement",path)));
        }
        return result;
    }
    private static String projectKey(String label) {return switch(label.replaceAll("\\s","").toLowerCase(Locale.ROOT)) {
        case "项目名称","项目名","project","projectname" -> "name";
        case "项目目标","目标","goal" -> "goal";
        case "本人职责","个人职责","个人贡献","我的职责","我的工作","我负责","responsibility","myresponsibilities" -> "responsibility";
        case "技术栈","使用技术","开发技术","技术选型","techstack","technologies" -> "techStack";
        case "遇到的困难","技术难点","项目难点","困难","difficulty" -> "difficulty";
        case "解决过程","解决方案","解决方法","solution" -> "solution";
        case "项目成果","个人成果","成果","效果","结果","result" -> "result";
        case "后续改进","改进","improvement" -> "improvement";
        // Stop a previous field when ambiguous team-wide descriptions begin.
        case "项目描述","项目介绍","团队职责","团队成果","项目职责","主要职责","负责内容" -> "unassigned";
        default -> null;
    };}
    private static String label(String line,String names) {Matcher m=Pattern.compile("^(?:"+names+")\\s*[:：]\\s*(.+)$").matcher(line.strip());return m.matches()?m.group(1).strip():null;}
    private static String join(String a,String b,String separator){return a.isBlank()?b:a.contains(b)?a:a+separator+b;}
    private String value(Map<String,String> fields,String key,String path){return bounded(fields.getOrDefault(key,""),5000,path+"."+key);}
    private void addTech(List<String> techs,String text){for(String part:text.split("[,，、;；|]")){String t=part.strip();if(!t.isBlank()&&t.length()<=128&&!techs.contains(t)&&techs.size()<100)techs.add(t);}}
    private String bounded(String value,int max,String field){if(value.length()<=max)return value;warnings.add(field+" 超过字段长度，已截取；完整文字保留在原文。");return value.substring(0,max);}
    private void proof(String field,String source,String confidence){evidence.add(new ResumeParser.Evidence(field,source,confidence));}
}
