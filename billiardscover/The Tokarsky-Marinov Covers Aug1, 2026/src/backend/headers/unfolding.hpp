#pragma once

#include "general.hpp"
#include "initial_angles.hpp"

struct Vertex final {
    // We use size_t here, because these numbers depend on the size of the unfolding.
    size_t number;
    size_t branch;

    explicit Vertex(const size_t number_, const size_t branch_)
        : number{number_}, branch{branch_} {}

    friend bool operator==(const Vertex& lhs, const Vertex& rhs) {
        return std::tie(lhs.number, lhs.branch) == std::tie(rhs.number, rhs.branch);
    }

    friend bool operator<(const Vertex& lhs, const Vertex& rhs) {
        return std::tie(lhs.number, lhs.branch) < std::tie(rhs.number, rhs.branch);
    }

    friend std::ostream& operator<<(std::ostream& os, const Vertex& vertex) {
        return os << '(' << vertex.number << ", " << vertex.branch << ')';
    }
};

struct Edge final {
    XYZ edge_type;
    LinComArrZ<XYPi> polar_angle;

    explicit Edge(const XYZ edge_type_, const LinComArrZ<XYPi>& polar_angle_)
        : edge_type{edge_type_}, polar_angle{polar_angle_} {}

    friend bool operator==(const Edge& lhs, const Edge& rhs) {
        return std::tie(lhs.edge_type, lhs.polar_angle) == std::tie(rhs.edge_type, rhs.polar_angle);
    }

    friend bool operator<(const Edge& lhs, const Edge& rhs) {
        return std::tie(lhs.edge_type, lhs.polar_angle) < std::tie(rhs.edge_type, rhs.polar_angle);
    }

    friend std::ostream& operator<<(std::ostream& os, const Edge& edge) {
        return os << '(' << edge.edge_type << ", " << edge.polar_angle << ')';
    }
};

class Unfolding final {
  private:
    std::map<std::pair<Vertex, Vertex>, Edge> edges;
    std::vector<Vertex> left_vertices;
    std::vector<Vertex> right_vertices;

    std::pair<EqMap<Sin>, EqMap<Cos>> path_vector(const std::vector<Vertex>& path) const;

  public:
    explicit Unfolding(const std::vector<CodeNumber>& tmp_code_numbers, const std::vector<XYZ>& tmp_code_angles);

    std::pair<EqMap<Sin>, EqMap<Cos>> shooting_vector_general() const;

    template <template <typename> class T, template <typename> class S>
    Curves generate_curves(const EqMap<T>& shooting_vector_x, const EqMap<S>& shooting_vector_y, const InitialAngles& initial_angles, const PointQ& center, const Rational& rx, const Rational& ry) const;
};

extern template Curves Unfolding::generate_curves(const EqMap<Sin>& shooting_vector_x, const EqMap<Cos>& shooting_vector_y, const InitialAngles& initial_angles, const PointQ& center, const Rational& rx, const Rational& ry) const;
extern template Curves Unfolding::generate_curves(const EqMap<Cos>& shooting_vector_x, const EqMap<Sin>& shooting_vector_y, const InitialAngles& initial_angles, const PointQ& center, const Rational& rx, const Rational& ry) const;
