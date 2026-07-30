#include <sstream>
#include "utils.hpp"
#include "conversion.hpp"

// Nick Shan, July, 2026
std::unordered_map<std::string, CodeType> stringToCodeType = {
    {"oso", CodeType::OSO},
    {"osno", CodeType::OSNO},
    {"ons", CodeType::ONS},
    {"cs", CodeType::CS},
    {"cns", CodeType::CNS}
};



/*
Jul 31 2025 Marco Mai
transfer from Java
*/
boost::optional<ClassifiedCodeSequence> convert(const std::vector<int>& codeList) {
    std::vector<int> newCode = codeList;
    const int len = static_cast<int>(newCode.size());
    int count = 0;

    // Rotate until first != last or max tries reached
    while (newCode[0] == newCode[len-1] && count < len + 1) {
        CodeSequence::rotateLeft(newCode);
        ++count;
    }

    if (count >= len) {
        return boost::none;
    }

    std::vector<int> finalList;
    int counter = 0;
    const int size = static_cast<int>(newCode.size());

    for (int i = 0; i < size; ++i) {
        ++counter;
        if (newCode[i] != newCode[(i + 1) % size]) {
            finalList.push_back(counter);
            counter = 0;
        }
    }


	boost::variant<InvalidCodeSequence,ClassifiedCodeSequence> either = ClassifiedCodeSequence::create(finalList);; // .value() may throw
	// if (std::holds_alternative<ClassifiedCodeSequence>(either)) {
    //     return std::get<ClassifiedCodeSequence>(either);
    // } else {
    //     return boost::none;
    // }

    if (boost::get<ClassifiedCodeSequence>(&either)) {
        const ClassifiedCodeSequence& codeSequence = *boost::get<ClassifiedCodeSequence>(&either);
        ClassifiedCodeSequence classCodeSeq(codeSequence);

    auto code_numbers = classCodeSeq.codeSequence->numbers();
    auto code_angles = classCodeSeq.codeSequence->angles(XYZ::X, XYZ::Y);

    auto code_angles_eta = falgo::transform(code_angles, xyz_to_xyeta);
    auto code_angles_pi = falgo::transform(code_angles, xyz_to_xypi);

        return classCodeSeq;
    } else {
        return boost::none;
    }

}

/*
Jun 23 2026 Jeff Khuu
gives a neat string of the code with information about it. Ported from Utils.java.
*/
std::string standard(ClassifiedCodeSequence code, const int count) {
    std::ostringstream oss; 
    const CodeType type = code.codeType;

    oss << count;
    if(count < 10) oss << " ";
    oss << " - " << type;
    if(type == CodeType::CS || type != CodeType::OSNO) oss << " ";
    oss << "(" << code.codeLength << ", " << code.codeSum << ") " << code.toString();
    return oss.str();
}

/*
Jul 31 2025 Marco Mai
checking if such code type valid sequence, return code type
*/
boost::optional<CodeType> getCodeType(const std::vector<int32_t>& codeList) {
    std::vector<int32_t> newCode = codeList;
    int32_t len = static_cast<int32_t>(newCode.size());
    int32_t count = 0;

    // Try all rotations, break early if first != last
    while (count <= len && newCode.front() == newCode.back()) {
        std::rotate(newCode.begin(), newCode.begin() + 1, newCode.end());
        ++count;
    }

    if (count > len) {
        return boost::none; // all rotations had first == last
    }

    std::vector<int32_t> finalList;
    int32_t counter = 0;
    int32_t size = static_cast<int32_t>(newCode.size());

    for (int32_t i = 0; i < size; ++i) {
        ++counter;
        if (newCode[i] != newCode[(i + 1) % size]) {
            finalList.push_back(counter);
            counter = 0;
        }
    }


    // Create a CodeSequence using the static create() method
    try{
        boost::variant<InvalidCodeSequence, CodeSequence> result = CodeSequence::create(finalList);
        if (CodeSequence* codeSeq = boost::get<CodeSequence>(&result)){
            CodeType codetype = codeSeq ->type();
            return codetype;
        }

    }catch (std::exception& e)  {
        return boost::none;
    }
    return boost::none;

}

/*
Jul 31 2025 Marco Mai
transfer from Java
*/
int32_t modN(int32_t x, int32_t n) {
    // while (x >= n) {
    //     x -= n;
    // }
    // while (x < 0) {
    //     x += n;
    // }
    // return x;
    x = std::remainder(x, n);  // result in (-n/2, n/2]
    return (x < 0) ? x + n : x;
}




std::string to_lower(const std::string& str) {
    std::string res = str;
    std::transform(res.begin(), res.end(), res.begin(),
                   [](unsigned char c) { return std::tolower(c); });
    return res;
}

std::vector<CodeType> parse_code_types(const std::string& input,
                                       const std::unordered_map<std::string, CodeType>& lookup) {
    std::vector<CodeType> result;
    std::istringstream iss(input);
    std::string word;
    while (iss >> word) {
        auto it = lookup.find(to_lower(word));
        if (it != lookup.end()) {
            result.push_back(it->second);
        }
    }
    return result;
}

bool is_code_type_in_list(CodeType code, const std::vector<CodeType>& allowed) {
    return std::find(allowed.begin(), allowed.end(), code) != allowed.end();
}