#!/bin/env python3

import logging
import operator
import os
import re
import subprocess
import sys
import xml.dom.minidom as minidom

logging.basicConfig(stream=sys.stdout, level=logging.DEBUG, format="%(levelname)s - %(message)s")

audit_start_pattern = re.compile(r"^\[INFO\] Ignored \d+ errors, \d+ violations remaining\.$")
audit_end_pattern = re.compile(r"^\[INFO\] You have \d+ Checkstyle violations\. The maximum number of allowed violations is \d+\.$")
#audit_line_pattern = re.compile(r"^\[WARN\] (.*): (.*)\. \[(.*)\]$")
audit_line_pattern = re.compile(r"^\[WARNING\] .*:\[\d+(?:,\d+)?] \(.*\) (.*?): .*$")
max_allowed_violations = -1

def checkstyle_stats():
    maven_project_dir = sys.argv[1]
    maven_project_dir_path = os.path.abspath(maven_project_dir)
    pom_file = os.path.join(maven_project_dir_path, "pom.xml")
    if not os.path.isfile(pom_file):
        logging.error("Can't find pom.xml file in %s" % maven_project_dir_path)
        sys.exit(1)
    
    logging.info("Processing pom.xml file in %s" % maven_project_dir_path)
    os.chdir(maven_project_dir_path)

    pom = minidom.parse(open(pom_file))
    pom_properties = pom.getElementsByTagName("properties")
    if pom_properties is not None:
        for property_tag in pom_properties:
            checkstyle_tag = property_tag.getElementsByTagName("checkstyle.maxAllowedViolations")
            if checkstyle_tag is not None and checkstyle_tag.length == 1:
                global max_allowed_violations
                max_allowed_violations = int(checkstyle_tag[0].firstChild.data)
                break
            else:
                logging.error("Can't find checkstyle.maxAllowedViolations")
    else:
        logging.error("Can't find properties tag in pom.xml file")
    logging.debug("max allowed violations = %d" % max_allowed_violations)

    try:
        maven = subprocess.check_output(["mvn", "checkstyle:check"], universal_newlines=True)
        checkstyle_audit = maven.split("\n")
        logging.debug("mvn checkstyle:check finished with %d lines of output" % len(checkstyle_audit))
    except subprocess.CalledProcessError as e:
        logging.error("command mvn checkstyle:check failed")
        sys.exit(1)

    audit = {}
    start = audit_start(checkstyle_audit)
    end = audit_end(checkstyle_audit)

    for line in checkstyle_audit[start:end]:
        regex = audit_line_pattern.match(line)
        if regex:
            category = regex.group(1)
            if category in audit:
                audit[category] += 1
            else:
                audit[category] = 1
        else:
            logging.warning("Line did not match audit pattern:\n" + line)
    
    print_results(audit)

def audit_start(lines):
    start = 0
    for line_no, line in enumerate(lines):
        #if "[INFO] Starting audit..." == line:
        if audit_start_pattern.match(line):
            logging.debug("Found start of audit on line %d %s" % (line_no, line))
            start = line_no + 1
            break
    return start

def audit_end(lines):
    end = 0
    for line_no, line in enumerate(lines):
        #if "Audit done." == line:
        if audit_end_pattern.match(line):
            logging.debug("Found end of audit on line %d %s" % (line_no, line))
            end = line_no
            break
    return end

def print_results(results):
    total = sum(results.values())
    print("Total checkstyle warnings %d. Maximum allowed violations %d." % (total, max_allowed_violations))
    if total > max_allowed_violations:
        print("PR sanity check will fail until you reduce CheckStyle violations")
    elif max_allowed_violations > total:
        print("Update pom.xml and set checkstyle.maxAllowedViolations to %d" % total)
    data = sorted(results.items(), key=operator.itemgetter(1), reverse=True)
    for category, count in data:
        print("% 5.1f%% %4d %s" % (((count / total) * 100), count, category))

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: %s dir_with_pom_file" % sys.argv[0])
        sys.exit(1)
    checkstyle_stats()
